package app.edgehatch.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.edgehatch.launcher.databinding.ActivityMainBinding
import app.edgehatch.launcher.databinding.ItemAppChoiceBinding

/**
 * The only user-facing screen. Overlay-only onboarding folded in, plus the live
 * settings: enable, handle side, vertical position, width, height, opacity,
 * tap/swipe triggers and the app selection. Each validated change is persisted
 * atomically to [EdgePreferences] (which clamps and bumps the config version)
 * and then reconciled into the running service via an explicit
 * apply — no service restart. `autoStart` is only stored (applies next boot).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: EdgePreferences
    private lateinit var repo: LauncherAppRepository

    private var updatingUi = false
    private val applyHandler = Handler(Looper.getMainLooper())
    private val applyRunnable = Runnable { pushConfiguration() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = EdgePreferences(this)
        repo = LauncherAppRepository(this)

        // Show the running version so a sideloaded reinstall is verifiable on-device.
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            null
        }
        binding.headerSummary.text =
            getString(R.string.header_summary) + (version?.let { "\n\nBuild v$it" } ?: "")

        binding.enableSwitch.setOnCheckedChangeListener(::onEnableToggled)
        binding.grantOverlayButton.setOnClickListener { requestOverlayPermission() }
        binding.batteryButton.setOnClickListener { openBatterySettings() }
        binding.accessibilityButton.setOnClickListener { openAccessibilitySettings() }

        binding.autoStartSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) prefs.autoStart = checked // stored only; next boot
        }

        binding.sideToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (updatingUi || !isChecked) return@addOnButtonCheckedListener
            prefs.handleSide =
                if (checkedId == R.id.button_side_left) HandleSide.LEFT else HandleSide.RIGHT
            scheduleApply()
        }

        // Value bubble on the thumb while dragging.
        binding.verticalSlider.setLabelFormatter { "${it.toInt()} %" }
        binding.widthSlider.setLabelFormatter { "${it.toInt()} dp" }
        binding.heightSlider.setLabelFormatter { "${it.toInt()} dp" }
        binding.opacitySlider.setLabelFormatter { "${it.toInt()} %" }

        binding.verticalSlider.addOnChangeListener { _, value, fromUser ->
            if (!updatingUi && fromUser) { prefs.handleVerticalBias = value / 100f; scheduleApply() }
            binding.verticalTitle.text = labelWithValue(R.string.vertical_title, value.toInt(), "%")
        }
        binding.widthSlider.addOnChangeListener { _, value, fromUser ->
            if (!updatingUi && fromUser) { prefs.handleWidthDp = value.toInt(); scheduleApply() }
            binding.widthTitle.text = labelWithValue(R.string.width_title, value.toInt(), "dp")
        }
        binding.heightSlider.addOnChangeListener { _, value, fromUser ->
            if (!updatingUi && fromUser) { prefs.handleHeightDp = value.toInt(); scheduleApply() }
            binding.heightTitle.text = labelWithValue(R.string.height_title, value.toInt(), "dp")
        }
        binding.opacitySlider.addOnChangeListener { _, value, fromUser ->
            if (!updatingUi && fromUser) { prefs.handleOpacity = value / 100f; scheduleApply() }
            binding.opacityTitle.text = labelWithValue(R.string.opacity_title, value.toInt(), "%")
        }

        binding.tapSwitch.setOnCheckedChangeListener { btn, checked -> onTriggerToggled(btn, checked, isTap = true) }
        binding.swipeSwitch.setOnCheckedChangeListener { btn, checked -> onTriggerToggled(btn, checked, isTap = false) }

        binding.appsList.layoutManager = LinearLayoutManager(this)
        loadChoices()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        if (prefs.enabled) activateDrawPath()
    }

    override fun onDestroy() {
        applyHandler.removeCallbacks(applyRunnable)
        super.onDestroy()
    }

    /** Which mechanism should draw right now (single source of truth). */
    private fun currentDrawPath(): DrawPath = chooseDrawPath(
        enabled = prefs.enabled,
        accessibilityConnected = EdgeAccessibilityService.isConnected(),
        canDrawOverlays = Settings.canDrawOverlays(this),
    )

    /** Start the correct drawer and ensure the other one is not also drawing. */
    private fun activateDrawPath() {
        when (currentDrawPath()) {
            DrawPath.ACCESSIBILITY -> {
                EdgeOverlayService.stopNow(this) // yield: never a double handle
                EdgeAccessibilityService.refreshIfConnected()
            }
            DrawPath.OVERLAY -> EdgeOverlayService.start(this)
            DrawPath.NONE -> {
                EdgeOverlayService.stopNow(this)
                EdgeAccessibilityService.refreshIfConnected() // removes its views if disabled
            }
        }
    }

    private fun onEnableToggled(button: CompoundButton, checked: Boolean) {
        if (updatingUi) return
        if (checked) {
            val a11y = EdgeAccessibilityService.isConnected()
            if (chooseDrawPath(true, a11y, Settings.canDrawOverlays(this)) == DrawPath.NONE) {
                // No way to draw yet: revert and point at the overlay grant
                // (the accessibility route is offered by its own card).
                updatingUi = true
                button.isChecked = false
                updatingUi = false
                requestOverlayPermission()
                return
            }
            prefs.enabled = true
            activateDrawPath()
        } else {
            prefs.enabled = false
            EdgeOverlayService.stopNow(this) // removes FGS overlay views now
            EdgeAccessibilityService.refreshIfConnected() // a11y path removes its views (disabled)
        }
        refreshUi()
    }

    private fun onTriggerToggled(button: CompoundButton, checked: Boolean, isTap: Boolean) {
        if (updatingUi) return
        // Keep at least one trigger on: reverting the last one is not allowed.
        val otherOn = if (isTap) prefs.triggerSwipe else prefs.triggerTap
        if (!checked && !otherOn) {
            updatingUi = true
            button.isChecked = true
            updatingUi = false
            return
        }
        if (isTap) prefs.triggerTap = checked else prefs.triggerSwipe = checked
        scheduleApply()
    }

    /**
     * Open the "display over other apps" permission screen. The DJI RC 2's
     * stripped settings can lack the per-app deep-link, so try progressively
     * broader targets and never crash if none resolves.
     */
    private fun requestOverlayPermission() {
        val targets = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in targets) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
                // fall through to the next, broader fallback
            }
        }
        Toast.makeText(this, R.string.overlay_manual_hint, Toast.LENGTH_LONG).show()
    }

    /**
     * Open battery-optimization settings so the persistent service is not killed
     * on the RC 2. No permission needed; falls back to this app's detail page.
     */
    private fun openBatterySettings() {
        val targets = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in targets) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
                // fall through to the next fallback
            }
        }
        Toast.makeText(this, R.string.battery_manual_hint, Toast.LENGTH_LONG).show()
    }

    /**
     * Open the accessibility settings so the user can enable EdgeHatch's service.
     * Same fallback cascade as the overlay path for the RC 2's stripped settings.
     */
    private fun openAccessibilitySettings() {
        val targets = listOf(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in targets) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
                // fall through to the next fallback
            }
        }
        Toast.makeText(this, R.string.accessibility_manual_hint, Toast.LENGTH_LONG).show()
    }

    private fun labelWithValue(titleRes: Int, value: Int, unit: String): String =
        "${getString(titleRes)}:  $value $unit"

    /** Debounce rapid (slider) changes into a single apply. */
    private fun scheduleApply() {
        applyHandler.removeCallbacks(applyRunnable)
        applyHandler.postDelayed(applyRunnable, APPLY_DEBOUNCE_MS)
    }

    private fun pushConfiguration() {
        when (currentDrawPath()) {
            DrawPath.ACCESSIBILITY -> EdgeAccessibilityService.refreshIfConnected()
            DrawPath.OVERLAY -> EdgeOverlayService.applyConfiguration(this)
            DrawPath.NONE -> Unit
        }
    }

    private fun refreshUi() {
        updatingUi = true
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasA11y = EdgeAccessibilityService.isConnected()
        // Always keep the card visible so the overlay setting stays reachable and
        // its status is never ambiguous (the RC 2 has no easy Settings access).
        binding.overlayGrantedGroup.visibility = ViewGroup.VISIBLE
        binding.overlaySummary.setText(
            if (hasOverlay) R.string.overlay_summary_granted else R.string.overlay_summary,
        )
        binding.grantOverlayButton.setText(
            if (hasOverlay) R.string.overlay_open_setting else R.string.overlay_grant,
        )
        binding.accessibilitySummary.setText(
            if (hasA11y) R.string.accessibility_granted else R.string.accessibility_card_summary,
        )
        // The handle can draw via the overlay permission OR the accessibility service.
        binding.enableSwitch.isChecked = prefs.enabled && (hasOverlay || hasA11y)
        binding.autoStartSwitch.isChecked = prefs.autoStart
        binding.sideToggle.check(
            if (prefs.handleSide == HandleSide.LEFT) R.id.button_side_left else R.id.button_side_right,
        )
        binding.verticalSlider.value = (prefs.handleVerticalBias * 100f).coerceIn(0f, 100f)
        binding.widthSlider.value = prefs.handleWidthDp.toFloat()
            .coerceIn(EdgeLimits.WIDTH_MIN.toFloat(), EdgeLimits.WIDTH_MAX.toFloat())
        binding.heightSlider.value = prefs.handleHeightDp.toFloat()
            .coerceIn(EdgeLimits.HEIGHT_MIN.toFloat(), EdgeLimits.HEIGHT_MAX.toFloat())
        binding.opacitySlider.value = (prefs.handleOpacity * 100f)
            .coerceIn(EdgeLimits.OPACITY_MIN * 100f, EdgeLimits.OPACITY_MAX * 100f)
        binding.verticalTitle.text =
            labelWithValue(R.string.vertical_title, binding.verticalSlider.value.toInt(), "%")
        binding.widthTitle.text =
            labelWithValue(R.string.width_title, binding.widthSlider.value.toInt(), "dp")
        binding.heightTitle.text =
            labelWithValue(R.string.height_title, binding.heightSlider.value.toInt(), "dp")
        binding.opacityTitle.text =
            labelWithValue(R.string.opacity_title, binding.opacitySlider.value.toInt(), "%")
        binding.tapSwitch.isChecked = prefs.triggerTap
        binding.swipeSwitch.isChecked = prefs.triggerSwipe
        updatingUi = false
    }

    private fun loadChoices() {
        Thread {
            val apps = repo.loadLaunchableApps()
            runOnUiThread {
                binding.appsList.adapter = ChoiceAdapter(apps, prefs.selectedPackages) { pkg, selected ->
                    val current = prefs.selectedPackages.toMutableSet()
                    if (selected) current.add(pkg) else current.remove(pkg)
                    prefs.selectedPackages = current
                    scheduleApply() // refreshes an open panel live
                }
            }
        }.start()
    }

    private class ChoiceAdapter(
        private val items: List<AppEntry>,
        selected: Set<String>,
        private val onToggle: (String, Boolean) -> Unit,
    ) : RecyclerView.Adapter<ChoiceAdapter.VH>() {

        private val checked = HashSet(selected)

        class VH(val binding: ItemAppChoiceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemAppChoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = items[position]
            holder.binding.choiceIcon.setImageDrawable(app.icon)
            holder.binding.choiceLabel.text = app.label
            holder.binding.choiceCheck.setOnCheckedChangeListener(null)
            holder.binding.choiceCheck.isChecked = app.packageName in checked
            holder.binding.choiceCheck.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) checked.add(app.packageName) else checked.remove(app.packageName)
                onToggle(app.packageName, isChecked)
            }
            holder.binding.root.setOnClickListener {
                holder.binding.choiceCheck.isChecked = !holder.binding.choiceCheck.isChecked
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        private const val APPLY_DEBOUNCE_MS = 120L
    }
}
