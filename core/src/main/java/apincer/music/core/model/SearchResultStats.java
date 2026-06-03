package apincer.music.core.model;

public class SearchResultStats {
    private final int totalCount;
    private final long totalSize;
    private final double totalDuration;

    public SearchResultStats(int totalCount, long totalSize, double totalDuration) {
        this.totalCount = totalCount;
        this.totalSize = totalSize;
        this.totalDuration = totalDuration;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public double getTotalDuration() {
        return totalDuration;
    }
}
