# Lessons Learned

## Android UI & UX Harmonization

### Status Bar & Collapsed Toolbar Backgrounds
- **Problem**: When using `AppBarLayout` inside a `CoordinatorLayout` with `fitsSystemWindows="true"`, the system status bar background draws under the `AppBarLayout` container. If the container or nested `CollapsingToolbarLayout` defaults to the theme's primary color or uses a hardcoded scrim (e.g. `@color/colorPrimary`), it results in a jarring, non-theme-adaptive color (like bright light blue in Dark Mode).
- **Solution**: Avoid hardcoding hex colors or utilizing high-contrast primary colors for the toolbar background or status bar scrim. Instead, explicitly configure the components to adapt to the active DayNight theme:
  - Set `android:background="?attr/colorSurface"` on the `AppBarLayout`.
  - Set `app:contentScrim="?attr/colorSurface"` on the `CollapsingToolbarLayout`.
  - This ensures that in both collapsed and expanded states, the status bar and toolbar blend seamlessly into `#121212` (dark mode) or `#FFFFFF` (light mode).

### Glassmorphic Design and Solid Component Pitfalls
- **Problem**: When aiming for a premium glassmorphic or frosted glass visual aesthetic, having intermediate UI views (such as list item background selectors, bottom app bar containers, or device detail widgets) draw with solid opaque backgrounds (e.g. `?attr/colorSurface` or hardcoded `@color/material_color_blue_grey_800`) completely blocks the underlying blurred wallpaper/background container (`@id/main_background_blur`). This invalidates the frosted glass effect, making the UI feel segmented and opaque.
- **Solution**: 
  - Ensure that intermediate container item selectors (such as the default state of `selector_item.xml`) default to transparent (`@android:color/transparent`) so the background blur floats through unhindered.
  - Swap solid dark backgrounds for modern translucent equivalents (e.g. `90%` opacity dark colors like `#E6121212` or translucent white base layers like `#1AFFFFFF` - 10% opacity).
  - Swap solid dark/black borders/strokes with a fine, semi-transparent white stroke (`#33FFFFFF` - 20% opacity) to cleanly delineate glass edges against dark/blurred backgrounds.

## Clear Service/Server Manager UI States
- **Problem**: Showing a generic "Not Available" text for URL/connection fields when the server is offline or stopped causes user confusion regarding whether the issue lies with network connectivity or the server process.
- **Solution**: Always use clear, descriptive labels specifying both the field and the reason. For example, instead of a plain `"Not Available"`, display a text like `"Server URL: Not Available (Server Stopped)"` to explicitly communicate the state. Ensure that requirements messages (like `"Required WiFi network"`) are updated to include all supported connectivity modes (e.g. `"Required WiFi or Hotspot network"`).
