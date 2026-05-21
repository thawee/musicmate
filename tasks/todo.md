# Checklist: Harmonize UI to Premium Glassy / Frosted Theme

## Phase 1: Planning and Setup
- [x] Identify non-glassy components and formulate the implementation plan (completed)
- [x] Obtain user approval on the implementation plan (approved)

## Phase 2: Core Refactoring
- [x] Modify [selector_item.xml](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/drawable/selector_item.xml) to make default background transparent
- [x] Modify [shape_bottom_appbar_background.xml](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/drawable/shape_bottom_appbar_background.xml) to make bottom bar translucent
- [x] Modify [shape_device_background.xml](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/drawable/shape_device_background.xml) to use translucent white background and white border stroke
- [x] Modify [border_back_label.xml](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/drawable/border_back_label.xml) to use translucent white border stroke
- [x] Modify [shape_border_playing_black.xml](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/drawable/shape_border_playing_black.xml) to use translucent white border stroke

## Phase 3: Verification
- [x] Verify build compiles successfully using `./gradlew compileDebugJavaWithJavac`
- [x] Document final results in walkthrough and update task checklists
