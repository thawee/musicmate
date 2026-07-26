# Task Plan: Implement `:db-room` Module with AndroidX Room

Implement a complete, production-ready AndroidX Room database persistence layer in `:db-room` to replace `RoomDbHelperStub` and allow the `room` flavor dimension (`:app:assembleHttpcoreRoomDebug`) to function fully.

---

### Phase 1: Data Model & Room Entities Design (`:db-room`)
- Create `TrackEntity` annotated with `@Entity(tableName = "music_tag")` matching `OrmLiteHelper` / `AudioTag` schema and indices (`path`, `unique_key`, `title`, `artist`, `album`, `genre`, etc.).
- Create `SearchResultStats` helper / converter.
- Add Room TypeConverters for fields if necessary (`Date`, `List<String>`, `Map`).

---

### Phase 2: DAO & Database Interface (`:db-room`)
- Create `TrackDao` with clean SQL queries matching all `DbHelper` methods (`findByUniqueKey`, `findMySongs`, `findByPath`, `findByTitle`, `findRecentlyAdded`, `findHiRes`, `findMQASongs`, `findDSDSongs`, `getSearchStats`, etc.).
- Create `MusicRoomDatabase` extending `RoomDatabase`.

---

### Phase 3: RoomDbHelper Implementation & Hilt Integration (`:db-room`)
- Implement `RoomDbHelper` implementing `DbHelper` interface using `MusicRoomDatabase` and `TrackDao`.
- Map between domain model `Track` / `AudioTag` and Room `TrackEntity`.
- Update `DatabaseModule.java` `@Provides` method to return `RoomDbHelper` instead of `RoomDbHelperStub`.

---

### Phase 4: Verification & Build Validation
- Build `:app:assembleHttpcoreRoomDebug`.
- Verify database operations, scan persistence, and list population in the Android UI.

---

### Todo Checklist
- [x] Phase 1: Create `TrackEntity` in `:db-room`
- [x] Phase 2: Create `TrackDao` and `MusicRoomDatabase` in `:db-room`
- [x] Phase 3: Implement `RoomDbHelper` and update `DatabaseModule`
- [x] Phase 4: Verify with `./gradlew :app:assembleHttpcoreRoomDebug`

---

### Review

**Status: COMPLETE ✅** (2026-07-26)

All phases implemented and verified. Key commits:
- `7ba4d321` feat: `:db-room` module + database flavor dimension
- `4cb53809` feat(db-room): complete Room persistence layer (TrackEntity, TrackDao, MusicRoomDatabase, RoomDbHelper)
- `7f9c50b3` / `e05adfcb` fix(db-room): Hilt DatabaseModule non-null provider binding
- `995f6206` fix(server): null check for statsData in getLibraryStats
- `d409794d` fix(codec): StringIndexOutOfBoundsException in JThinkReader.parseId3Tags
- `6858f78e` fix(service): onMediaTrackChanged DB lookup offloaded to background thread

UI list confirmed working by user ✅
