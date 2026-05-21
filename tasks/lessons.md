# Lessons Learned

## Android UI & UX Harmonization

### Status Bar & Collapsed Toolbar Backgrounds
- **Problem**: When using `AppBarLayout` inside a `CoordinatorLayout` with `fitsSystemWindows="true"`, the system status bar background draws under the `AppBarLayout` container. If the container or nested `CollapsingToolbarLayout` defaults to the theme's primary color or uses a hardcoded scrim (e.g. `@color/colorPrimary`), it results in a jarring, non-theme-adaptive color (like bright light blue in Dark Mode).
- **Solution**: Avoid hardcoding hex colors or utilizing high-contrast primary colors for the toolbar background or status bar scrim. Instead, explicitly configure the components to adapt to the active DayNight theme:
  - Set `android:background="?attr/colorSurface"` on the `AppBarLayout`.
  - Set `app:contentScrim="?attr/colorSurface"` on the `CollapsingToolbarLayout`.
  - This ensures that in both collapsed and expanded states, the status bar and toolbar blend seamlessly into `#121212` (dark mode) or `#FFFFFF` (light mode).
