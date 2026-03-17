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

public class PartialFileProducer implements AsyncEntityProducer {
    private static final String TAG = "PartialFileProducer";
    private final File file;
    private final long start;
    private final long length;
    private final ContentType contentType;
    private RandomAccessFile raf;
    private FileChannel fileChannel;
    private long bytesProduced = 0;

    public PartialFileProducer(File file, long start, long length, ContentType contentType) {
        this.file = file;
        this.start = start;
        this.length = length;
        this.contentType = contentType;
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
        // Returns an estimate of bytes available to read
        long rem = length - bytesProduced;
        return rem > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rem;
    }

    @Override
    public void produce(DataStreamChannel channel) throws IOException {
        if (raf == null) {
            raf = new RandomAccessFile(file, "r");
            fileChannel = raf.getChannel();
            fileChannel.position(start);
        }

        long remaining = length - bytesProduced;
        if (remaining <= 0) {
            channel.endStream();
            return;
        }

        // Use 128KB buffer to match your SndBufSize for consistent high-res streaming
        int bufferSize = (int) Math.min(1024 * 1024, remaining);
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

        int read = fileChannel.read(buffer);
        if (read > 0) {
            buffer.flip();
            // channel.write returns the number of bytes actually written to the network
            int written = channel.write(buffer);
            bytesProduced += written;

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
        } catch (IOException ignore) {}
    }
}