package com.generated.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Local Smart Recommendation Engine:
 * Analyzes local watch behavior:
 * - Completed views count (highest weight: 3.5x)
 * - Views count (weight: 1.5x)
 * - Marked as favorite (weight: 4.0x)
 * - Keyword/Tag frequency learned from user's history and favorites
 * - Recency penalty/boost
 * - Candidate correlation when playing a specific video ("Up Next")
 */
public class RecommendationEngine {

    public static List<VideoItem> getRecommendedFeed(List<VideoItem> allVideos) {
        if (allVideos == null || allVideos.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. Build user profile: frequent keywords from liked and viewed videos
        Map<String, Double> tagAffinities = calculateUserTagAffinities(allVideos);

        // 2. Score each video
        long now = System.currentTimeMillis();
        for (VideoItem item : allVideos) {
            double score = 1.0;

            // Favorites boost
            if (item.isFavorite) {
                score += 5.0;
            }

            // Completion weight
            score += item.completedCount * 3.5;

            // Partial view weight
            score += item.viewCount * 1.5;

            // Tag/Keyword affinity match
            Set<String> words = extractKeywords(item.title + " " + item.tags);
            double tagScore = 0.0;
            for (String w : words) {
                if (tagAffinities.containsKey(w)) {
                    tagScore += tagAffinities.get(w);
                }
            }
            score += Math.min(tagScore, 10.0);

            // Freshness / Recency boost (for newly added offline files)
            long ageDays = Math.max(0, (now - item.dateAdded) / (1000 * 60 * 60 * 24));
            double recencyFactor = Math.max(0.2, 1.0 - (ageDays * 0.05));
            score *= recencyFactor;

            // Small random jitter so the list feels lively
            score += (Math.abs(item.id * 17) % 10) * 0.05;

            item.recommendationScore = score;
        }

        // Sort descending by score
        List<VideoItem> sorted = new ArrayList<>(allVideos);
        Collections.sort(sorted, new Comparator<VideoItem>() {
            @Override
            public int compare(VideoItem v1, VideoItem v2) {
                return Double.compare(v2.recommendationScore, v1.recommendationScore);
            }
        });

        return sorted;
    }

    public static List<VideoItem> getUpNextVideos(List<VideoItem> allVideos, VideoItem currentVideo) {
        List<VideoItem> candidates = new ArrayList<>();
        if (allVideos == null || allVideos.isEmpty()) return candidates;

        Set<String> currentKeywords = (currentVideo != null)
                ? extractKeywords(currentVideo.title + " " + currentVideo.tags)
                : new HashSet<String>();

        for (VideoItem item : allVideos) {
            if (currentVideo != null && item.id == currentVideo.id) {
                continue; // exclude currently playing
            }

            double score = 1.0;

            // Keyword overlap with current video
            Set<String> candWords = extractKeywords(item.title + " " + item.tags);
            int overlap = 0;
            for (String w : candWords) {
                if (currentKeywords.contains(w)) {
                    overlap++;
                }
            }
            score += overlap * 4.0;

            // General popularity boost
            score += item.viewCount * 1.2;
            score += item.completedCount * 2.0;
            if (item.isFavorite) score += 3.0;

            item.recommendationScore = score;
            candidates.add(item);
        }

        Collections.sort(candidates, new Comparator<VideoItem>() {
            @Override
            public int compare(VideoItem v1, VideoItem v2) {
                return Double.compare(v2.recommendationScore, v1.recommendationScore);
            }
        });

        return candidates;
    }

    private static Map<String, Double> calculateUserTagAffinities(List<VideoItem> allVideos) {
        Map<String, Double> weights = new HashMap<>();
        for (VideoItem item : allVideos) {
            double itemWeight = (item.completedCount * 2.5) + (item.viewCount * 1.0) + (item.isFavorite ? 3.0 : 0.0);
            if (itemWeight <= 0) continue;

            Set<String> words = extractKeywords(item.title + " " + item.tags);
            for (String w : words) {
                Double prev = weights.get(w);
                weights.put(w, (prev == null ? 0.0 : prev) + itemWeight);
            }
        }
        return weights;
    }

    public static Set<String> extractKeywords(String text) {
        Set<String> set = new HashSet<>();
        if (text == null) return set;
        String[] tokens = text.toLowerCase(Locale.getDefault()).split("[\\s_\\-\\.\\[\\]\\(\\),]+");
        for (String t : tokens) {
            t = t.trim();
            if (t.length() >= 3 && !isStopWord(t)) {
                set.add(t);
            }
        }
        return set;
    }

    private static boolean isStopWord(String word) {
        return word.equals("the") || word.equals("and") || word.equals("with")
                || word.equals("mp4") || word.equals("mkv") || word.equals("avi")
                || word.equals("של") || word.equals("על") || word.equals("את")
                || word.equals("הוא") || word.equals("היא") || word.equals("זה");
    }
}
