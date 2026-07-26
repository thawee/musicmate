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
- [ ] Phase 1: Create `TrackEntity` in `:db-room`
- [ ] Phase 2: Create `TrackDao` and `MusicRoomDatabase` in `:db-room`
- [ ] Phase 3: Implement `RoomDbHelper` and update `DatabaseModule`
- [ ] Phase 4: Verify with `./gradlew :app:assembleHttpcoreRoomDebug`
