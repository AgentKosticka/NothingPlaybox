package com.agentkosticka.playbox

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Use the launcher layout, including its controls, rather than a second drawing of the widget. */
@Composable
internal fun NativeWidgetPreview(modifier: Modifier, description: String, render: (Int, Int) -> RemoteViews) {
    AndroidView(
        modifier = modifier,
        factory = { PreviewHost(it) },
        update = { host ->
            host.contentDescription = description
            host.render = render
            host.refresh()
        },
    )
}

private class PreviewHost(context: Context) : FrameLayout(context) {
    var render: ((Int, Int) -> RemoteViews)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) refresh()
    }

    fun refresh() {
        if (width <= 0 || height <= 0) return
        val views = render?.invoke(width, height) ?: return
        removeAllViews()
        addView(views.apply(context, this), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // A preview describes the widget as a whole; its controls belong to the home screen.
        getChildAt(0).importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        measureChildPreview()
    }

    private fun measureChildPreview() {
        getChildAt(0)?.let { child ->
            child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            child.layout(0, 0, width, height)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        measureChildPreview()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
