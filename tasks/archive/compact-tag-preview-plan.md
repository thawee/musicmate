# Plan: Compact Tag Preview

## Objective and evidence
- User confirmed the folder is reachable by scrolling. The remaining issue is first-screen density, not inaccessible content.
- Aim to show artwork, title, audio badges, genre, artist, album, and folder together on the supplied portrait-phone layout at default text scale.
- Keep the existing five bottom actions and their behavior. Preserve scrolling for smaller windows, larger text, long metadata, and batch mode.
- Status: implemented; debug build and app tests pass. Device visual checkpoint remains pending.

## Proposed design
- Center the full square cover at about 240dp on the reference phone, capped at 280dp on larger windows. Derive the final size from available content width and height above the dock; never crop or distort the cover to make it fit.
- Keep Back and Change Cover as matched 48dp screen-edge controls near the top of the artwork region, independent of the cover's narrower bounds.
- Retain the bold title with two-line support. Use the same dark surface for title and metadata to reduce visual fragmentation.
- Keep audio badges in a wrapping group. Place the genre chip in that same flow when space allows, retaining a 48dp touch target. It wraps naturally on narrow screens.
- Consolidate Artist, Album, and Folder into one full-width metadata surface with subtle dividers. Rows retain the vector icon, field label, readable value, explicit track count, and chevron.
- Target 56–64dp rows for ordinary text, 8–12dp internal spacing, and natural growth for larger fonts or long values. Reduce repeated borders and outer gaps before reducing text size.
- Use consistent 16dp content margins. Keep color distinctions for meaningful audio/status badges and blue for metadata navigation.

## Ordered tasks

### 1. Compact artwork and title
**Dependencies:** None. **Scope:** Small, layout and activity.
- Size the artwork against the measured preview viewport; maintain its square aspect and existing artwork action.
- Anchor the two cover controls consistently and unify the title surface with the preview background.
**Acceptance:** Full cover visible, controls at least 48dp and centered, title supports two lines, no dock overlap.
**Likely files:** `activity_tags.xml`, `TagsActivity.java`.
**Verification:** Build resources/Java; inspect portrait, landscape, and preview/editor round-trip.

### 2. Compact metadata grouping
**Dependencies:** Task 1. **Scope:** Small, Compose header.
- Combine genre and audio attributes into a wrapping group.
- Replace individually bordered metadata cards with a single grouped surface and aligned rows.
**Acceptance:** Labels/counts remain explicit; all rows retain their related-track navigation; large text wraps without overlapping counts or chevrons.
**Likely files:** `AudioBadges.kt`.
**Verification:** Compile Compose; inspect ordinary, long, missing, and batch metadata.

### Checkpoint: Visual review
- Compare against the supplied screenshot using the same track where possible.
- At the reference phone size/default font, all three metadata rows should be visible above the dock without scrolling.
- If that target compromises cover prominence or text readability, revise the sizing before proceeding; scrolling remains the accessibility fallback.

### 3. Validation and documentation
**Dependencies:** Tasks 1–2. **Scope:** Small.
- Run `./gradlew :app:assembleDebug :app:testDebugUnitTest` and `git diff --check`.
- Verify five bottom actions, artwork selection, metadata navigation, scrolling, and preview/editor transitions.
- Check 320dp-wide and reference-sized portrait windows, landscape, and increased font scale. Verify 48dp interactive targets and screen-reader labels.
- Update `UI.md`, `CHANGELOG.md`, and task results with actual behavior and verification limits.
- Device interaction previously blocked by access to mandatory instructions; report any continued block rather than claiming visual verification.

## Risks
- Artwork can become too small when fitting every field: prioritize readable metadata and a useful cover size, allow scroll in constrained windows.
- Shared cover panel is reparented between preview/editor: verify both directions and preserve draft artwork.
- Genre's 48dp target can make the badge group taller: use wrapping rather than squeezing targets or shrinking text.

## Approval
- Review proposed compact layout and sizing with the user before implementation.
- Approved by the user (“do it”). Implemented using 38% of the available preview height, clamped to 180–280dp and available width. This keeps artwork useful in short windows while allowing scroll as planned.
