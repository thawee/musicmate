package apincer.android.storage;

public class StorageInfo {
    long free = 0;

    public long getFree() {
        return free;
    }

    public void setFree(long free) {
        this.free = free;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    long total = 0;
    String name;

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    String shortName;

    public int getPercentUsage() {
        return (int)(((total-free)*100)/total);
    }
}
