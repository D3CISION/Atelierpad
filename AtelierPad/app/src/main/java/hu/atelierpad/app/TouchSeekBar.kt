package hu.atelierpad.app

import android.content.Context
import android.view.MotionEvent
import android.widget.SeekBar

/**
 * Own a drag as soon as it starts, before an enclosing (Horizontal)ScrollView can
 * steal it. Native SeekBar behavior, keyboard and accessibility are preserved.
 * Tool type is intentionally NOT filtered: finger, stylus and mouse work alike.
 */
class TouchSeekBar(context:Context):SeekBar(context) {
    init { minimumHeight=context.dp(52);isFocusable=true }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(!isEnabled)return false
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE -> parent?.requestDisallowInterceptTouchEvent(true)
        }
        val handled=super.onTouchEvent(event)
        if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL || !handled)
            parent?.requestDisallowInterceptTouchEvent(false)
        return handled
    }
    override fun onDetachedFromWindow() {
        parent?.requestDisallowInterceptTouchEvent(false)
        super.onDetachedFromWindow()
    }
}
