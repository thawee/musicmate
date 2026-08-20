import re

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "r") as f:
    content = f.read()

# Replace populateQueueSection again to include updateQueueTabLabel
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
        updateQueueTabLabel(queue.size());
        
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

with open("app/src/main/java/apincer/android/mmate/ui/view/AudioHubBottomSheet.java", "w") as f:
    f.write(content)
