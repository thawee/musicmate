import re

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "r") as f:
    content = f.read()

# Add QueueState field
if "private QueueState queueState" not in content:
    content = content.replace(
        "private MediaServerState mediaServerState = new MediaServerState();",
        "private MediaServerState mediaServerState = new MediaServerState();\n    private apincer.android.mmate.ui.compose.QueueState queueState = new apincer.android.mmate.ui.compose.QueueState(new ArrayList<>(), null);"
    )

# Replace viewQueuePage inflation
inflate_target = "viewQueuePage = LayoutInflater.from(getContext()).inflate(R.layout.view_audio_hub_queue_page, null);"
inflate_replacement = """
        viewQueuePage = DialogInterop.createQueuePageView(
            requireContext(),
            queueState,
            track -> {
                if (isPlaybackServiceBound && playbackService != null) {
                    playbackService.playSong(track);
                    if (viewNowPlayingPage != null) populateNowPlayingSheet(viewNowPlayingPage);
                    populateQueueSection(viewQueuePage);
                }
            },
            (track, index) -> {
                QueueManager qm = playbackService != null ? playbackService.getQueueManager() : null;
                if (qm != null) {
                    qm.removeTrack(index);
                }
            },
            () -> {
                QueueManager qm = playbackService != null ? playbackService.getQueueManager() : null;
                if (qm != null) {
                    qm.emptyPlayingQueue();
                    Toast.makeText(getContext(), "Queue cleared", Toast.LENGTH_SHORT).show();
                    populateQueueSection(viewQueuePage);
                }
            },
            () -> {
                Toast.makeText(getContext(), "Jumped to playing track", Toast.LENGTH_SHORT).show();
            }
        );
"""
content = content.replace(inflate_target, inflate_replacement)

# Replace populateQueueSection entirely
populate_queue_start = content.find("private void populateQueueSection(@NonNull View root) {")
if populate_queue_start != -1:
    brace_count = 0
    populate_queue_end = -1
    for i in range(populate_queue_start, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                populate_queue_end = i + 1
                break
                
    new_populate = """private void populateQueueSection(@NonNull View root) {
        QueueManager qm = playbackService != null ? playbackService.getQueueManager() : null;
        if (qm != null) {
            qm.loadPlayingQueue();
        }
        List<Track> queue = (qm != null) ? new ArrayList<>(qm.getSongs()) : new ArrayList<>();
        Track track = playbackService != null ? playbackService.getNowPlayingSong() : null;
        String currentKey = (track != null) ? track.getUniqueKey() : null;
        
        queueState.updateTracks(queue);
        queueState.setCurrentPlayingKey(currentKey);
        
        // Duration calculation
        double totalDur = 0;
        for (Track t : queue) {
            totalDur += t.getAudioDuration();
        }
        if (totalDur > 0) {
            int mins = (int) totalDur / 60;
            int secs = (int) totalDur % 60;
            queueState.setTotalDurationText(String.format(java.util.Locale.US, "%d:%02d", mins, secs));
        } else {
            queueState.setTotalDurationText("");
        }
    }"""
    content = content[:populate_queue_start] + new_populate + content[populate_queue_end:]

# Remove QueueAdapter class
adapter_start = content.find("private static class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.VH> {")
if adapter_start != -1:
    brace_count = 0
    adapter_end = -1
    for i in range(adapter_start, len(content)):
        if content[i] == '{':
            brace_count += 1
        elif content[i] == '}':
            brace_count -= 1
            if brace_count == 0:
                adapter_end = i + 1
                break
    content = content[:adapter_start] + content[adapter_end:]

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "w") as f:
    f.write(content)
print("Patched AudioHubBottomSheet for Queue")
