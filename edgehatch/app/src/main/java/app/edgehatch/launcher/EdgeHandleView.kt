package app.edgehatch.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * Pure gesture decision, extracted so it is unit-testable without an Android
 * runtime. A movement opens the panel if it is a tap (within [slop]) and taps
 * are enabled, or an inward swipe and swipes are enabled. A swipe must move
 * inward past [slop] AND be horizontally dominant (`abs(dx) > abs(dy)`), so a
 * long vertical drag with a slight inward drift over DJI Fly does not open it.
 * With both disabled it never triggers (the config layer keeps at least one on).
 */
fun isEdgeTrigger(
    dx: Float,
    dy: Float,
    slop: Float,
    onLeft: Boolean,
    tapEnabled: Boolean,
    swipeEnabled: Boolean,
): Boolean {
    val isTap = abs(dx) < slop && abs(dy) < slop
    val inwardPastSlop = if (onLeft) dx > slop else dx < -slop
    val horizontallyDominant = abs(dx) > abs(dy)
    val inwardSwipe = inwardPastSlop && horizontallyDominant
    return (tapEnabled && isTap) || (swipeEnabled && inwardSwipe)
}

/**
 * A slim, translucent handle drawn at one screen edge. It lives inside its own
 * narrow window only; a tap or a short inward swipe within its bounds invokes
 * [onTrigger]. It never inspects or intercepts touches outside itself, so the
 * rest of the screen (e.g. DJI Fly) keeps every touch. Side, trigger mode and
 * opacity are updated live by [EdgeOverlayService].
 */
@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class EdgeHandleView(
    context: Context,
    var onLeft: Boolean,
    private val onTrigger: () -> Unit,
) : View(context) {

    var tapEnabled: Boolean = true
    var swipeEnabled: Boolean = true

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.edge_handle)
    }
    private val corner = resources.getDimension(R.dimen.edge_handle_corner)

    private var downX = 0f
    private var downY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, corner, corner, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                val slop = resources.getDimension(R.dimen.edge_trigger_slop)
                if (isEdgeTrigger(dx, dy, slop, onLeft, tapEnabled, swipeEnabled)) onTrigger()
                return true
            }
        }
        return false
    }
}
