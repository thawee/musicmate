package apincer.android.mmate.worker;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import apincer.android.mmate.Constants;
import apincer.android.mmate.codec.FFMpegHelper;
import apincer.android.mmate.codec.TagWriter;
import apincer.android.mmate.notification.AudioTagEditResultEvent;
import apincer.android.mmate.repository.FileRepository;
import apincer.android.mmate.repository.MusicAnalyser;
import apincer.android.mmate.repository.MusicTag;
import apincer.android.mmate.repository.TagRepository;
import apincer.android.mmate.utils.MusicTagUtils;
import apincer.android.utils.FileUtils;

/**
 * Utility class to handle file operations in background threads
 * with progress reporting capabilities
 */
public class FileOperationTask {
    private static final String TAG = "FileOperationTask";
    private static final double MAX_PROGRESS = 100.0;

    /**
     * Interface for reporting progress of file operations
     */
    public interface ProgressCallback {
        /**
         * Called when progress is made on a file operation
         * @param tag The music tag being processed
         * @param progress The progress value (0-100)
         * @param status Status message
         */
        void onProgress(MusicTag tag, int progress, String status);

        /**
         * Called when all operations are complete
         */
        void onComplete();
    }

    /**
     * Delete multiple media files
     */
    public static void deleteFiles(@NonNull Context context,
                                   @NonNull List<MusicTag> selections,
                                   @NonNull ProgressCallback callback) {
        final FileRepository repository = FileRepository.newInstance(context);
        final AtomicInteger count = new AtomicInteger(0);
        final double rate = MAX_PROGRESS / selections.size();

        for (MusicTag tag : selections) {
            MusicMateExecutors.executeParallel(() -> {
                try {
                    boolean status = repository.deleteMediaItem(tag);
                    int progress = (int) Math.ceil(count.incrementAndGet() * rate);

                    if (status) {
                        AudioTagEditResultEvent message = new AudioTagEditResultEvent(
                                AudioTagEditResultEvent.ACTION_DELETE,
                                Constants.STATUS_SUCCESS,
                                tag);
                        EventBus.getDefault().postSticky(message);
                        callback.onProgress(tag, progress, "Deleted");
                    } else {
                        callback.onProgress(tag, progress, "Failed");
                    }

                    if (count.get() == selections.size()) {
                        callback.onComplete();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error deleting file", e);
                    callback.onProgress(tag, (int) Math.ceil(count.incrementAndGet() * rate), "Error");
                    if (count.get() == selections.size()) {
                        callback.onComplete();
                    }
                }
            });
        }
    }

    /**
     * Move/import files to music directory
     */
    public static void moveFiles(@NonNull Context context,
                                 @NonNull List<MusicTag> selections,
                                 @NonNull ProgressCallback callback) {
        final FileRepository repository = FileRepository.newInstance(context);
        final AtomicInteger count = new AtomicInteger(0);
        final double rate = MAX_PROGRESS / selections.size();

        for (MusicTag tag : selections) {
            MusicMateExecutors.executeParallel(() -> {
                try {
                    callback.onProgress(tag, (int)(count.get() * rate), "Moving");
                    boolean status = repository.importAudioFile(tag);
                    int progress = (int) Math.ceil(count.incrementAndGet() * rate);

                    if (status) {
                        AudioTagEditResultEvent message = new AudioTagEditResultEvent(
                                AudioTagEditResultEvent.ACTION_MOVE,
                                Constants.STATUS_SUCCESS,
                                tag);
                        EventBus.getDefault().postSticky(message);
                        callback.onProgress(tag, progress, "Done");
                    } else {
                        callback.onProgress(tag, progress, "Failed");
                    }

                    if (count.get() == selections.size()) {
                        callback.onComplete();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error moving file", e);
                    callback.onProgress(tag, (int) Math.ceil(count.incrementAndGet() * rate), "Error");
                    if (count.get() == selections.size()) {
                        callback.onComplete();
                    }
                }
            });
        }
    }

    /**
     * Encode audio files to different format
     */
    public static void encodeFiles(@NonNull Context context,
                                   @NonNull List<MusicTag> selections,
                                   @NonNull String targetFormat,
                                   int compressionLevel,
                                   @NonNull ProgressCallback callback) {
        final FileRepository repository = FileRepository.newInstance(context);
        final AtomicInteger count = new AtomicInteger(0);
        final double rate = MAX_PROGRESS / selections.size();

        MusicMateExecutors.execute(() -> {
            for (MusicTag tag : selections) {
                try {
                    callback.onProgress(tag, (int)(count.get() * rate), "Encoding");

                    String srcPath = tag.getPath();
                    String filePath = FileUtils.removeExtension(tag.getPath());
                    String targetExt = targetFormat.toLowerCase();
                    String targetPath = filePath + "." + targetExt;
                    int bitDepth = tag.getAudioBitsDepth();

                    boolean success = FFMpegHelper.convert(
                            context,
                            srcPath,
                            targetPath,
                            compressionLevel,
                            bitDepth);

                    int progress = (int) Math.ceil(count.incrementAndGet() * rate);

                    if (success) {
                        // Re-scan the new file
                        repository.scanMusicFile(new File(targetPath), true);
                        callback.onProgress(tag, progress, "Done");
                    } else {
                        callback.onProgress(tag, progress, "Failed");
                    }

                    if (count.get() == selections.size()) {
                        callback.onComplete();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error encoding file", e);
                    callback.onProgress(tag, (int) Math.ceil(count.incrementAndGet() * rate), "Error");
                    if (count.get() == selections.size()) {
                        callback.onComplete();
                    }
                }
            }
        });
    }

    /**
     * Determine the appropriate output format based on the input file type
     */
    public static String determineOutputFormat(MusicTag tag) {
        if (MusicTagUtils.isAIFFile(tag) ||
                MusicTagUtils.isALACFile(tag) ||
                MusicTagUtils.isDSD(tag) ||
                MusicTagUtils.isWavFile(tag)) {
            return "flac";
        } else if (MusicTagUtils.isAACFile(tag)) {
            return "mp3";
        } else if (MusicTagUtils.isFLACFile(tag)) {
            // Default to AIFF when converting from FLAC
            return "aiff";
        }
        // Default to FLAC for any other format
        return "flac";
    }

    /**
     * Measure dynamic range for multiple files
     */
    public static void measureDR(@NonNull Context context,
                                 @NonNull List<MusicTag> selections,
                                 @NonNull ProgressCallback callback) {
        final AtomicInteger count = new AtomicInteger(0);
        final double rate = MAX_PROGRESS / selections.size();

        for (MusicTag tag : selections) {
            MusicMateExecutors.execute(() -> {
                try {
                    callback.onProgress(tag, (int)(count.get() * rate), "Evaluating");

                    boolean success = MusicAnalyser.process(tag);
                    int progress = (int) Math.ceil(count.incrementAndGet() * rate);

                    if (success) {
                        // Write updated tags back to file
                        TagWriter.writeTagToFile(context, tag);
                        // Update tag in repository
                        TagRepository.saveTag(tag);

                        AudioTagEditResultEvent message = new AudioTagEditResultEvent(
                                AudioTagEditResultEvent.ACTION_UPDATE,
                                Constants.STATUS_SUCCESS,
                                tag);
                        EventBus.getDefault().postSticky(message);

                        callback.onProgress(tag, progress, "Success");
                    } else {
                        callback.onProgress(tag, progress, "Failed");
                    }

                    if (count.get() == selections.size()) {
                        callback.onComplete();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error measuring DR", e);
                    callback.onProgress(tag, (int) Math.ceil(count.incrementAndGet() * rate), "Error");
                    if (count.get() == selections.size()) {
                        callback.onComplete();
                    }
                }
            });
        }
    }

    /**
     * Calculate initial progress value
     * This provides a small initial progress value to show task has started
     */
    public static int getInitialProgress(int totalItems) {
        // Give a small initial progress (1% of total)
        return Math.max(1, (int)(MAX_PROGRESS / (totalItems * 100)));
    }
}
