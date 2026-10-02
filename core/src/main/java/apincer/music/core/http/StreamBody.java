package apincer.music.core.http;

/**
 * Marks a response that streams media (a file, or audio converted while it is sent): it holds a
 * stream slot, counts as an active stream and can be evicted for a new stream.
 */
interface StreamBody {
}
