package apincer.music.core.http;

/**
 * Marks a response that streams media (a file, or audio converted while it is sent): it holds a
 * bounded transfer slot. Static resources use a separate limit from media streams.
 */
interface StreamBody {
    default boolean isMedia() { return true; }
}
