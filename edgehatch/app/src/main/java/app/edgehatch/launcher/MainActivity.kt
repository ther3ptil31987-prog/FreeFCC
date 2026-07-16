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

        binding.enableSwitch.setOnCheckedChangeListener(::onEnableToggled)
        binding.grantOverlayButton.setOnClickListener { requestOverlayPermission() }

        binding.autoStartSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) prefs.autoStart = checked // stored only; next boot
        }

        binding.sideToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (updatingUi || !isChecked) return@addOnButtonCheckedListener
            prefs.handleSide =
                if (checkedId == R.id.button_side_left) HandleSide.LEFT else HandleSide.RIGHT
            scheduleApply()
        }

        binding.verticalSlider.addOnChangeListener { _, value, fromUser ->
            if (!updatingUi && fromUser) { prefs.handleVerticalBias = value / 100f; scheduleApply() }
        }
        binding.widthSlider.addOnChangeListener { _, value, fromUser ->
            if (!updatingUi && fromUser) { prefs.handleWidthDp = value.toInt(); scheduleApply() }
        }
        binding.heightSlider.addOnChangeListener { _, value, fromUser ->
            if (!updatingUi && fromUser) { prefs.handleHeightDp = value.toInt(); scheduleApply() }
        }
        binding.opacitySlider.addOnChangeListener { _, value, fromUser ->
            if (!updatingUi && fromUser) { prefs.handleOpacity = value / 100f; scheduleApply() }
        }

        binding.tapSwitch.setOnCheckedChangeListener { btn, checked -> onTriggerToggled(btn, checked, isTap = true) }
        binding.swipeSwitch.setOnCheckedChangeListener { btn, checked -> onTriggerToggled(btn, checked, isTap = false) }

        binding.appsList.layoutManager = LinearLayoutManager(this)
        loadChoices()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        if (prefs.enabled && Settings.canDrawOverlays(this)) {
            EdgeOverlayService.start(this)
        }
    }

    override fun onDestroy() {
        applyHandler.removeCallbacks(applyRunnable)
        super.onDestroy()
    }

    private fun onEnableToggled(button: CompoundButton, checked: Boolean) {
        if (updatingUi) return
        if (checked) {
            if (!Settings.canDrawOverlays(this)) {
                updatingUi = true
                button.isChecked = false
                updatingUi = false
                requestOverlayPermission()
                return
            }
            prefs.enabled = true
            EdgeOverlayService.start(this)
        } else {
            prefs.enabled = false
            EdgeOverlayService.stop(this) // disable path: removes all overlay views now
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

    private fun requestOverlayPermission() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
        )
    }

    /** Debounce rapid (slider) changes into a single apply. */
    private fun scheduleApply() {
        applyHandler.removeCallbacks(applyRunnable)
        applyHandler.postDelayed(applyRunnable, APPLY_DEBOUNCE_MS)
    }

    private fun pushConfiguration() {
        if (prefs.enabled && Settings.canDrawOverlays(this)) {
            EdgeOverlayService.applyConfiguration(this)
        }
    }

    private fun refreshUi() {
        updatingUi = true
        val hasOverlay = Settings.canDrawOverlays(this)
        binding.overlayGrantedGroup.visibility = if (hasOverlay) ViewGroup.GONE else ViewGroup.VISIBLE
        binding.enableSwitch.isChecked = prefs.enabled && hasOverlay
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
