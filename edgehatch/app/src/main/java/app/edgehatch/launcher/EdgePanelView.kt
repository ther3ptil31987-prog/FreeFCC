package app.edgehatch.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.edgehatch.launcher.databinding.ItemEdgeAppBinding
import app.edgehatch.launcher.databinding.OverlayEdgePanelBinding

/**
 * The app panel shown when the handle is triggered: a dimmed scrim plus a small
 * card listing the user-selected apps. Selecting an app calls [onAppSelected];
 * tapping the scrim or pressing Back calls [onDismiss]. Attached to the window
 * only while open and fully removed on dismiss, so it occupies input solely
 * during a deliberate selection. While open, [updateApps] refreshes the list in
 * place without a service restart.
 */
@SuppressLint("ViewConstructor")
class EdgePanelView(
    context: Context,
    apps: List<AppEntry>,
    onLeft: Boolean,
    private val onAppSelected: (AppEntry) -> Unit,
    private val onDismiss: () -> Unit,
) : FrameLayout(context) {

    private val binding = OverlayEdgePanelBinding.inflate(LayoutInflater.from(context), this)
    private val adapter = AppAdapter(apps.toMutableList(), onAppSelected)

    init {
        binding.scrim.setOnClickListener { onDismiss() }

        val lp = binding.panelCard.layoutParams as LayoutParams
        lp.gravity = (if (onLeft) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
        binding.panelCard.layoutParams = lp

        binding.panelList.layoutManager = LinearLayoutManager(context)
        binding.panelList.adapter = adapter
        renderEmpty(apps.isEmpty())

        // Focusable so Back dismisses the panel while it is open.
        isFocusableInTouchMode = true
        requestFocus()
    }

    /** Refresh the panel's app list while it stays open (live config change). */
    fun updateApps(apps: List<AppEntry>) {
        adapter.replace(apps)
        renderEmpty(apps.isEmpty())
    }

    private fun renderEmpty(empty: Boolean) {
        binding.emptyLabel.visibility = if (empty) View.VISIBLE else View.GONE
        binding.panelList.visibility = if (empty) View.GONE else View.VISIBLE
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onDismiss()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private class AppAdapter(
        private val items: MutableList<AppEntry>,
        private val onClick: (AppEntry) -> Unit,
    ) : RecyclerView.Adapter<AppAdapter.VH>() {

        class VH(val binding: ItemEdgeAppBinding) : RecyclerView.ViewHolder(binding.root)

        @SuppressLint("NotifyDataSetChanged")
        fun replace(newItems: List<AppEntry>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemEdgeAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = items[position]
            holder.binding.appIcon.setImageDrawable(app.icon)
            holder.binding.appLabel.text = app.label
            holder.binding.root.setOnClickListener { onClick(app) }
        }

        override fun getItemCount(): Int = items.size
    }
}
