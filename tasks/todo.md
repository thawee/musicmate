# Fix AlbumArtist Save Bug and Missing Dropdown Adapter

Fix issue where `albumArtist` is mistakenly overwritten by `artist` during tag save, and set up missing autocomplete dropdown adapter for `txtAlbumArtist`.

---

### Plan Tasks
- [ ] 1. Update `TagsEditorFragment.java` to fix `albumArtist` save bug on line 455 (`tagUpdate.setAlbumArtist(buildTag(txtAlbumArtist, tagUpdate.getAlbumArtist()))`).
- [ ] 2. Add `getAlbumArtistList()` to `TagRepository.java` to collect unique album artists from DB and default lists.
- [ ] 3. Update `TagsEditorFragment.java` to initialize adapter for `txtAlbumArtist` using `tagRepos.getAlbumArtistList()`.
- [ ] 4. Verify build and code correctness.
