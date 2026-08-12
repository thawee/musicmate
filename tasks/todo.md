# Sparkle Symbol/Icon Design for "NEW" (Unmanaged Track) Indicator

## Objectives
Replace the plain yellow dot overlay on cover art in music lists and the plain yellow text block in Tag Activity with a unified, premium Sparkle/Starburst (✦) symbol badge icon design system.

## Tasks
- [x] 1. Create vector drawables:
  - `ic_new_sparkle_badge.xml`: Gold/Amber circular badge containing a sharp 4-point sparkle starburst cutout (`auto_awesome`) for unmanaged tracks.
  - `ic_new_download_sparkle_badge.xml`: Cyan/Emerald circular badge for newly downloaded tracks.
- [x] 2. Update cover art overlay in music list item layouts:
  - Update `view_list_music_tag.xml`, `view_list_item.xml`, `view_list_item2.xml`, `view_list_item_compared.xml` to use 14dp x 14dp `ImageView` (`item_new_label`) displaying `@drawable/ic_new_sparkle_badge` anchored to `top|end`.
- [x] 3. Redesign Tag Activity `NewIndicatorView`:
  - Update `view_new_indicator.xml` layout to a pill chip featuring the sparkle vector icon + "NEW" typography.
  - Update `backgound_new_indicator.xml` to a sleek pill background with rounded corners (`12dp`).
  - Update `NewIndicatorView.java` to dynamically display sparkle icon & text for regular new vs downloaded tracks.
- [x] 4. Update Java adapters (`MusicTagAdapter.java`):
  - Ensure `holder.mNewLabelView` displays the appropriate sparkle badge (`ic_new_sparkle_badge` vs `ic_new_download_sparkle_badge`) and toggles visibility cleanly.
- [x] 5. Compile and verify clean build (`./gradlew compileDebugSources`).
- [x] 6. Document updates in `DESIGN.md` and `CHANGELOG.md`.

## Review & Results
- **Sparkle Badge System Implemented**:
  - Cover art on music lists uses 14dp vector badge icon overlays (`ic_new_sparkle_badge.xml` in Gold, `ic_new_download_sparkle_badge.xml` in Cyan) anchored at `top|end`.
  - Tag Activity replaces the plain yellow text block with a `12dp` rounded pill chip (`NewIndicatorView`) containing the Sparkle icon + "NEW" typography on dark amber (`#2E2712`) / dark cyan (`#0D2E3D`) chip backgrounds.
- **Verification**: Cleanly compiled with `./gradlew compileDebugSources` (`BUILD SUCCESSFUL`).
