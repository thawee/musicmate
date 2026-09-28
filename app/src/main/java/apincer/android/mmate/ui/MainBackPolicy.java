package apincer.android.mmate.ui;

/**
 * Single decision point for the main screen Back button. Exists so the precedence
 * between the navigation drawer, overlays, contextual selection, transient library state,
 * and typed library navigation is unit testable.
 */
public final class MainBackPolicy {

    public enum Action {
        CLOSE_DRAWER,
        DISMISS_OVERLAY,
        FINISH_SELECTION,
        CLEAR_SEARCH,
        NAVIGATE_LIBRARY,
        EXIT
    }

    private MainBackPolicy() {
    }

    /** Navigation 3 policy used by the typed route layer. */
    public static Action resolve(
            boolean drawerOpen,
            boolean overlayOpen,
            boolean selectionActive,
            boolean searchActive,
            boolean atRoot) {
        if (drawerOpen) {
            return Action.CLOSE_DRAWER;
        }
        if (overlayOpen) {
            return Action.DISMISS_OVERLAY;
        }
        if (selectionActive) {
            return Action.FINISH_SELECTION;
        }
        if (searchActive) {
            return Action.CLEAR_SEARCH;
        }
        return atRoot ? Action.EXIT : Action.NAVIGATE_LIBRARY;
    }
}
