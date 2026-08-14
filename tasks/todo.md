# TagsActivity & Song Info Screen UI/UX Fix Plan

## Objectives
Fix layout collisions, tab overlap, duplicated artist metadata, and text readability on the Song Detail / Tag Editor screen (`TagsActivity`).

## Tasks

- [x] **1. Fix Bottom Action Capsule & TabLayout Collision**
  - [x] Updated `TagsActivity.java` (`OffSetChangeListener` / `setupActionButtons`) so that when the header is fully expanded in Preview mode (`mode == 0`), `tabLayout` is hidden to prevent visual collision behind the bottom action buttons (`Delete` / `Organize` / `More...`).
  - [x] When collapsed or entering Editor mode (`mode == 1`), `tabLayout` cleanly displays at the top under the action bar.
  - [x] Styled `tabLayout` and `bottom_navigation_container` with frosted surfaces and elevation.

- [x] **2. Deduplicate Artist & Streamline Metadata Display**
  - [x] In `TagsActivity.java` (`updateTitlePanel`), only display `Album Artist` if it exists AND differs from `Artist` (e.g. Various Artists or featured compilations).
  - [x] If `Album Artist` is identical to `Artist`, suppressed the duplicate text and cleanly display `❖ Genre ❖` without redundant names.
  - [x] Streamlined tag/origin/mood layout and hide empty tag views.

- [x] **3. Top Artwork Title Readability & Scrim**
  - [x] Enhanced `shape_background_main_header.xml` with a smooth dark top-down gradient for clean title contrast across all album covers.

- [x] **4. Verification & Testing**
  - [x] Ran `./gradlew compileDebugSources testDebugUnitTest` (`BUILD SUCCESSFUL in 4s`, 0 errors).

## Review & Results
- **No More Tab Overlap:** The `Song Info` / `Tech Info` tabs are no longer shoved behind the `Delete` / `Organize` / `More...` action capsule in preview mode.
- **Clean Metadata:** Removed redundant artist repetition (`ต่าย อรทัย` above `ต่าย อรทัย`), now cleanly showing `Artist | Album` followed by `❖ Luk Thung ❖`.
- **Top Scrim:** Song title text is now crisp and readable with smooth contrast on any cover art.
