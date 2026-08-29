package apincer.android.jupnp.server.httpcore;

import android.util.Log;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.Set;

import apincer.music.core.playback.AudioStreamCacheManager;

public class PartialFileProducer implements AsyncEntityProducer {
    private static final String TAG = "PartialFileProducer";
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB optimized for consistent streaming

    private final File file;
    private final long start;
    private final long length;
    private final ContentType contentType;
    private RandomAccessFile raf;
    private FileChannel fileChannel;
    private long bytesProduced = 0;
    private ByteBuffer buffer;
    private byte[] preloadedBytes;
    private int preloadedOffset = 0;

    public PartialFileProducer(File file, long start, long length, ContentType contentType) {
        this.file = file;
        this.start = start;
        this.length = length;
        this.contentType = contentType;
        if (start == 0 && file != null) {
            this.preloadedBytes = AudioStreamCacheManager.getInstance().getPreloadedHead(file.getAbsolutePath());
        }
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public long getContentLength() {
        return length;
    }

    @Override
    public String getContentType() {
        return contentType != null ? contentType.toString() : null;
    }

    @Override
    public String getContentEncoding() {
        return null;
    }

    @Override
    public int available() {
        long rem = length - bytesProduced;
        return rem > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rem;
    }

    @Override
    public void produce(DataStreamChannel channel) throws IOException {
        long remainingInRequest = length - bytesProduced;
        if (remainingInRequest <= 0) {
            channel.endStream();
            return;
        }

        // 1. Serve from preloaded in-memory cache if available at start
        if (preloadedBytes != null && preloadedOffset < preloadedBytes.length && start == 0) {
            int availableInPreload = preloadedBytes.length - preloadedOffset;
            int toWrite = (int) Math.min(remainingInRequest, Math.min(BUFFER_SIZE, availableInPreload));
            ByteBuffer memBuf = ByteBuffer.wrap(preloadedBytes, preloadedOffset, toWrite);
            int written = channel.write(memBuf);
            if (written > 0) {
                preloadedOffset += written;
                bytesProduced += written;
            }
            if (bytesProduced >= length) {
                channel.endStream();
                return;
            }
            if (preloadedOffset < preloadedBytes.length) {
                return; // Continue writing preloaded bytes in next produce() iteration
            }
        }

        // 2. Stream from FileChannel once preloaded bytes are exhausted or if starting beyond 0
        if (raf == null) {
            raf = new RandomAccessFile(file, "r");
            fileChannel = raf.getChannel();
            fileChannel.position(start + bytesProduced);
            buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        }

        remainingInRequest = length - bytesProduced;
        if (remainingInRequest <= 0) {
            channel.endStream();
            return;
        }

        buffer.clear();
        if (remainingInRequest < BUFFER_SIZE) {
            buffer.limit((int) remainingInRequest);
        }

        int read = fileChannel.read(buffer);
        if (read > 0) {
            buffer.flip();
            int written = channel.write(buffer);
            bytesProduced += written;

            // If we didn't write the whole buffer, rewind file position for unwritten part
            if (buffer.hasRemaining()) {
                fileChannel.position(fileChannel.position() - buffer.remaining());
            }

            if (bytesProduced >= length) {
                channel.endStream();
            }
        } else if (read == -1) {
            channel.endStream();
        }
    }

    @Override
    public void failed(Exception ex) {
        Log.w(TAG, "Streaming failed: " + ex.getMessage());
        releaseResources();
    }

    @Override
    public Set<String> getTrailerNames() {
        return Collections.emptySet();
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public void releaseResources() {
        try {
            if (raf != null) {
                raf.close();
                raf = null;
                fileChannel = null;
            }
            buffer = null; // Help GC
            preloadedBytes = null;
        } catch (IOException ignore) {}
    }
}