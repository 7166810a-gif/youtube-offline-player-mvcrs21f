package com.generated.app;

public class VideoItem {
    public long id;
    public String uri;
    public String path;
    public String title;
    public long duration; // ms
    public long size; // bytes
    public long dateAdded;
    public int viewCount;
    public int completedCount;
    public boolean isFavorite;
    public String tags;
    public long lastWatched;
    public double recommendationScore;

    public String getFormattedDuration() {
        if (duration <= 0) return "00:00";
        long totalSec = duration / 1000;
        long sec = totalSec % 60;
        long min = (totalSec / 60) % 60;
        long hours = totalSec / 3600;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, min, sec);
        } else {
            return String.format("%02d:%02d", min, sec);
        }
    }

    public String getFormattedSize() {
        if (size <= 0) return "0 MB";
        double mb = size / (1024.0 * 1024.0);
        if (mb >= 1000.0) {
            double gb = mb / 1024.0;
            return String.format("%.1f GB", gb);
        }
        return String.format("%.1f MB", mb);
    }
}
