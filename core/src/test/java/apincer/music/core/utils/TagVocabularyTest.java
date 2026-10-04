package apincer.music.core.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Set;

public class TagVocabularyTest {

    @Test
    public void legacyGenrePresetsReadBackAsOneGenre() {
        // The old preset was written as "R&B/Soul" and read back as "R&B, Soul"
        assertEquals("R&B", TagVocabulary.normalize(TagVocabulary.Field.GENRE, "R&B, Soul"));
        assertEquals("R&B", TagVocabulary.normalize(TagVocabulary.Field.GENRE, "R&B / Soul"));
        assertEquals("Electronic", TagVocabulary.normalize(TagVocabulary.Field.GENRE, "Electronic/EDM"));
        assertEquals("Soundtrack, Jazz", TagVocabulary.normalize(TagVocabulary.Field.GENRE, "Soundtrack, OST, Jazz"));
    }

    @Test
    public void ordinaryGenresAreKept() {
        assertEquals("Pop, Rock", TagVocabulary.normalize(TagVocabulary.Field.GENRE, "Pop, Rock"));
        assertEquals("Luk Thung", TagVocabulary.normalize(TagVocabulary.Field.GENRE, "Luk Thung"));
        assertEquals("Reggae & Ska", TagVocabulary.normalize(TagVocabulary.Field.GENRE, "Reggae & Ska"));
    }

    @Test
    public void legacyMoodStyleAndOriginMapToCurrentNames() {
        assertEquals("Focus", TagVocabulary.normalize(TagVocabulary.Field.MOOD, "Focus / Study"));
        assertEquals("Chill", TagVocabulary.normalize(TagVocabulary.Field.MOOD, "chill / relax"));
        assertEquals("Live", TagVocabulary.normalize(TagVocabulary.Field.STYLE, "Live / Concert"));
        assertEquals("Western", TagVocabulary.normalize(TagVocabulary.Field.ORIGIN, "US/UK"));
        assertEquals("Other Asian", TagVocabulary.normalize(TagVocabulary.Field.ORIGIN, "Asia"));
        assertEquals("Thai", TagVocabulary.normalize(TagVocabulary.Field.ORIGIN, "Thai"));
    }

    @Test
    public void matchKeysListEachGenreLowerCase() {
        Set<String> keys = TagVocabulary.matchKeys(TagVocabulary.Field.GENRE, "Pop, Rock");
        assertEquals(2, keys.size());
        assertTrue(keys.contains("pop"));
        assertTrue(keys.contains("rock"));
        assertTrue(TagVocabulary.matchKeys(TagVocabulary.Field.GENRE, "Alternative, Indie").contains("alternative"));
        assertEquals("focus", TagVocabulary.ruleKey(TagVocabulary.Field.MOOD, "Focus / Study"));
    }
}
