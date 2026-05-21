# Checklist: Fix Status Bar and Collapsed Toolbar Color UX

## Phase 1: Planning and Setup
- [x] Analyze the status bar and contentScrim light blue color root causes
- [x] Create implementation plan and obtain approval

## Phase 2: Core Refactoring
- [x] Modify [activity_tags.xml](file:///Users/thawee.p/Workspaces/github/musicmate/app/src/main/res/layout/activity_tags.xml) to apply `android:background="?attr/colorSurface"` to `AppBarLayout` and `app:contentScrim="?attr/colorSurface"` to `CollapsingToolbarLayout`

## Phase 3: Verification
- [x] Verify build compiles successfully using `./gradlew compileDebugJavaWithJavac`
- [x] Document final results in walkthrough
