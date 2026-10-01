package apincer.music.core.http;

/** Limits how many files SonicNIO streams at once. */
interface StreamSlots {
    /** Takes a slot, evicting the least recently active stream if all are taken; false if none can be freed. */
    boolean acquire();

    /** Returns a slot taken by {@link #acquire()}. */
    void release();
}
