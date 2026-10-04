"""Run the checked-in migration and DAO SQL against SQLite (no Android device)."""
import pathlib
import re
import sqlite3
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
ROOM = ROOT / "db-room/src/main/java/apincer/music/room"


class ListeningHistorySqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.execute("CREATE TABLE musictag (id INTEGER PRIMARY KEY, title TEXT)")
        self.db.executemany("INSERT INTO musictag VALUES (?, ?)", [(1, "Old"), (2, "New"), (3, "Skipped")])
        migration = re.search(r'db.execSQL\("([^"]+)"\)', (ROOM / "MusicRoomDatabase.java").read_text()).group(1)
        self.db.execute(migration)
        self.queries = re.findall(r'@Query\("([^"]+)"\)', (ROOM / "dao/ListeningHistoryDao.java").read_text())

    def tearDown(self):
        self.db.close()

    def seed(self, track_id):
        self.db.execute("INSERT INTO listening_history VALUES (?, 0, 0, 0, '', '', '')", (track_id,))

    def test_migration_preserves_library_and_unplayed_includes_untracked(self):
        self.assertEqual(3, self.db.execute("SELECT COUNT(*) FROM musictag").fetchone()[0])
        self.assertEqual({1, 2, 3}, {r[0] for r in self.db.execute(self.queries[3])})

    def test_completed_and_skip_events_are_idempotent_and_independent(self):
        self.seed(1)
        self.seed(3)
        for _ in range(2):
            self.db.execute(self.queries[0], dict(id=1, session="play-1", wall=100))
            self.db.execute(self.queries[1], dict(id=1, session="play-1"))
            self.db.execute(self.queries[2], dict(id=3, session="skip-1"))
        self.assertEqual((1, 100), self.db.execute("SELECT completedPlays, lastPlayedMs FROM listening_history WHERE trackId=1").fetchone())
        self.assertEqual(1, self.db.execute("SELECT skips FROM listening_history WHERE trackId=3").fetchone()[0])
        self.assertEqual({2, 3}, {r[0] for r in self.db.execute(self.queries[3])})
        self.db.execute(self.queries[1], dict(id=1, session="play-2"))
        self.assertEqual(2, self.db.execute("SELECT completedPlays FROM listening_history WHERE trackId=1").fetchone()[0])

    def test_rediscover_cutoff_oldest_first_and_recent_partial_listen(self):
        for track_id, last_played in [(1, 100), (2, 50)]:
            self.seed(track_id)
            self.db.execute(self.queries[0], dict(id=track_id, session="old", wall=last_played))
            self.db.execute(self.queries[1], dict(id=track_id, session="old"))
        self.assertEqual([2, 1], [r[0] for r in self.db.execute(self.queries[4], dict(cutoff=100))])
        self.assertEqual([2], [r[0] for r in self.db.execute(self.queries[4], dict(cutoff=99))])
        self.db.execute(self.queries[0], dict(id=2, session="recent-partial", wall=200))
        self.assertEqual([1], [r[0] for r in self.db.execute(self.queries[4], dict(cutoff=100))])


if __name__ == "__main__":
    unittest.main()
