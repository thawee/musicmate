-- Preserved from Room-generated version 2 createAllTables, 2026-10-03.
CREATE TABLE IF NOT EXISTS `musictag` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uniqueKey` TEXT NOT NULL, `albumArtFilename` TEXT, `path` TEXT, `fileType` TEXT, `fileLastModified` INTEGER NOT NULL, `fileSize` INTEGER NOT NULL, `isManaged` INTEGER NOT NULL, `storageId` TEXT, `simpleName` TEXT, `audioEncoding` TEXT, `qualityInd` TEXT, `mqaSampleRate` INTEGER NOT NULL, `audioChannels` TEXT, `audioBitsDepth` INTEGER NOT NULL, `audioSampleRate` INTEGER NOT NULL, `audioBitRate` INTEGER NOT NULL, `audioDuration` REAL NOT NULL, `audioStartTime` REAL NOT NULL, `title` TEXT, `normalizedTitle` TEXT, `artist` TEXT, `normalizedArtist` TEXT, `album` TEXT, `year` TEXT, `genre` TEXT, `mood` TEXT, `style` TEXT, `origin` TEXT, `track` TEXT, `comment` TEXT, `composer` TEXT, `albumArtist` TEXT, `compilation` INTEGER NOT NULL, `publisher` TEXT, `drScore` REAL NOT NULL, `dynamicRange` REAL NOT NULL, `bpm` REAL NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS `index_musictag_uniqueKey` ON `musictag` (`uniqueKey`);
CREATE INDEX IF NOT EXISTS `index_musictag_qualityInd` ON `musictag` (`qualityInd`);
CREATE INDEX IF NOT EXISTS `index_musictag_audioBitsDepth` ON `musictag` (`audioBitsDepth`);
CREATE INDEX IF NOT EXISTS `index_musictag_title` ON `musictag` (`title`);
CREATE INDEX IF NOT EXISTS `index_musictag_artist` ON `musictag` (`artist`);
CREATE INDEX IF NOT EXISTS `index_musictag_normalizedTitle` ON `musictag` (`normalizedTitle`);
CREATE INDEX IF NOT EXISTS `index_musictag_normalizedArtist` ON `musictag` (`normalizedArtist`);
CREATE INDEX IF NOT EXISTS `index_musictag_album` ON `musictag` (`album`);
CREATE INDEX IF NOT EXISTS `index_musictag_genre` ON `musictag` (`genre`);
CREATE INDEX IF NOT EXISTS `index_musictag_publisher` ON `musictag` (`publisher`);
CREATE TABLE IF NOT EXISTS `listening_history` (`trackId` INTEGER NOT NULL, `lastPlayedMs` INTEGER NOT NULL, `completedPlays` INTEGER NOT NULL, `skips` INTEGER NOT NULL, `lastStartedSession` TEXT NOT NULL, `lastCompletedSession` TEXT NOT NULL, `lastSkippedSession` TEXT NOT NULL, PRIMARY KEY(`trackId`));
CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT);
INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '2856760d86a4790db8f60091f9750240');
