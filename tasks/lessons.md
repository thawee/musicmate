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

## Java NIO Server Stability & Resource Cleanup

### Guarding Against Double-Close Sockets/Channels
- **Problem**: In multi-threaded Java NIO reactor loops, client disconnect events and timeouts can trigger concurrent or redundant `closeConnection()` calls. When this happens, a simple counter like `activeConnections.decrementAndGet()` is decremented multiple times, corrupting server metrics and causing stability issues on long runs.
- **Solution**: Atomically clear and check the selection key's attachment (e.g., using `key.attach(null)`) as a synchronization/guard mechanism. If the attachment is already null, bypass connection teardown logic and active-connection decrements.

### Leak Prevention of Pools and Request Objects
- **Problem**: If a client socket disconnects abruptly or times out before the HTTP request body is fully read by the reactor, the acquired `HttpRequest` object from the object pool is left on the `ConnectionAttachment` and leaked (never reset and returned to the pool).
- **Solution**: During `closeConnection()`, inspect if the connection attachment has a non-null `request`. If so, explicitly call `request.reset()` and return it to the `requestPool`.

### Event Loop CPU 100% Spin Protection (JDK Epoll Bug)
- **Problem**: The Java NIO Selector can occasionally experience spurious wakeups where `select()` returns 0 immediately without waiting for the timeout or receiving keys. Under certain conditions, this loops infinitely, pegging the CPU to 100% and causing the server to slow down and freeze.
- **Solution**: Measure the duration of each `select()` call. If it returns 0 faster than a reasonable threshold (e.g., < 50ms) repeatedly (e.g., > 10 consecutive times), apply a small backoff sleep (e.g., `Thread.sleep(20)`) in the reactor thread to keep CPU usage low while keeping the selector active.

### Highly Efficient WebSocket Write Queueing (O(1) vs O(N))
- **Problem**: Periodically checking all registered selector keys (O(N) iteration over `selector.keys()`) on every tick of the reactor loop to register `OP_WRITE` for WebSockets with pending frames is highly inefficient and causes performance bottlenecks under high concurrency.
- **Solution**: Implement a thread-safe queue (`pendingWebSocketWrites`) to hold connections that have new outgoing frames. The event loop can then perform a fast O(M) poll of only the active writing connections, avoiding expensive synchronized iterations over all selector keys.

## Android List Navigation UX (State & Scroll Position)

### Avoiding Unnecessary Reloads & Retaining Scroll Context
- **Problem**: When navigating back from a detail/edit activity (like `TagsActivity`) to a list activity (like `MainActivity`), reloading the entire list unconditionally on every return (even on Back/Cancel) causes unnecessary CPU/database work and resets the list scroll position. This forces users to lose their place, creating a poor user experience.
- **Solution**: 
  - Use `setResult(RESULT_OK)` in the detail activity only when data was actually modified (saved, deleted, or moved).
  - Check `result.getResultCode() == AppCompatActivity.RESULT_OK` in the list activity launcher callback, and skip reloading if it is `RESULT_CANCELED`.
  - In the LiveData observer that updates the list adapter, save the LayoutManager scroll state (`onSaveInstanceState()`) before updating the adapter and restore it (`onRestoreInstanceState(state)`) immediately after.

### Paging-Aware List Reload
- **Problem**: When a scroll list supports pagination (e.g. page size of 500), reloading the list by querying page 0 from the database upon returning from a detail view loses all items beyond the first page. If the user was editing an item at index 700, the list will shrink to 500 items and the scroll state restore will fail, resetting the scroll to the top.
- **Solution**:
  - Implement a `reload` mechanism that queries up to the current count (`Math.max(1, currentPage) * PAGE_SIZE`) from the database, ensuring all loaded pages are refreshed in a single operation.
  - Dynamically recalculate pagination pointers (like `currentPage` and `isLastPage`) based on the count of items returned to ensure the list state transitions smoothly and scroll positions are successfully restored.

### Toggle Group Listener Accumulation & Target Bugs
- **Problem**: Calling configuration methods (like `setupActionButtons`) repeatedly during view lifecycle changes adds multiple check listeners using `addOnButtonCheckedListener()` on `MaterialButtonToggleGroup`. This causes actions (like Save or Reload) to fire multiple times on a single click, and registering on the wrong view group leaves UI actions completely broken.
- **Solution**:
  - Always call `clearOnButtonCheckedListeners()` on toggle groups before registering a new listener dynamically.
  - Verify target views matches the active fragment context layout.

### Predictable Back Navigation (Decoupling Scroll States)
- **Problem**: Overriding Back pressed handling to change system navigation based on view layout properties (e.g. forcing an AppBar to expand first when collapsed) breaks the user's expectation of the system Back button and behaves unpredictably when navigating read-only tabs.
- **Solution**: Keep scroll states separate from navigation logic. The Back button should predictably close/finish the active screen directly, unless blocked by a specific unsaved changes prompt.


