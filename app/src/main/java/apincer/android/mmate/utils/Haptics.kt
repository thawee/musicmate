package apincer.android.mmate.utils

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Centralized haptic feedback taxonomy for consistent tactile responses across the app.
 * Use these instead of raw HapticFeedbackConstants for semantic clarity and consistency.
 */
object Haptics {

    /**
     * Light tap - for standard button presses, list item clicks, transport controls.
     * Maps to [HapticFeedbackConstants.KEYBOARD_TAP] (VIRTUAL_KEY on API 29+).
     */
    fun selection(view: View) = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

    /**
     * Confirmation tap - for successful actions (save, add to queue, apply changes).
     * Maps to [HapticFeedbackConstants.VIRTUAL_KEY] - slightly stronger than selection.
     */
    fun success(view: View) = view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

    /**
     * Warning/negative tap - for destructive actions (delete, remove, clear queue).
     * Maps to [HapticFeedbackConstants.LONG_PRESS] - distinct heavier feel.
     */
    fun warning(view: View) = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

    /**
     * Navigation transition - for screen changes, drawer open/close, sheet expand/collapse.
     * Maps to [HapticFeedbackConstants.CONTEXT_CLICK] - subtle directional cue.
     */
    fun navigation(view: View) = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /**
     * Drag/move gesture - for reorder handles, drag-to-reorder, slider thumb grab.
     * Maps to [HapticFeedbackConstants.TEXT_HANDLE_MOVE] - precise movement feedback.
     */
    fun drag(view: View) = view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)

    /**
     * Toggle/switch - for shuffle, repeat, boolean settings, mode switches.
     * Maps to [HapticFeedbackConstants.KEYBOARD_TAP] with FLAG_IGNORE_VIEW_SETTING.
     */
    fun toggle(view: View) = view.performHapticFeedback(
        HapticFeedbackConstants.KEYBOARD_TAP,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    )

    /**
     * Error/blocked - for failed actions, disabled button presses, invalid drops.
     * Maps to [HapticFeedbackConstants.LONG_PRESS] with FLAG_IGNORE_VIEW_SETTING.
     */
    fun error(view: View) = view.performHapticFeedback(
        HapticFeedbackConstants.LONG_PRESS,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    )

    /**
     * Long-press action - for entering batch selection mode, context menus.
     * Maps to [HapticFeedbackConstants.LONG_PRESS] - standard Android long-press.
     */
    fun longPress(view: View) = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

    /**
     * Clock tick - for time-based controls (seekbar scrub, sleep timer increments).
     * Maps to [HapticFeedbackConstants.CLOCK_TICK] - subtle rhythmic feedback.
     */
    fun clockTick(view: View) = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}