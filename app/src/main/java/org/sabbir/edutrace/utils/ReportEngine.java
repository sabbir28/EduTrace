package org.sabbir.edutrace.utils;
import org.sabbir.edutrace.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sabbir.edutrace.data.models.StudySession;

public class ReportEngine {

    public static class StudyStats {
        public long totalTimeMillis;
        public int totalDistractions;
        public long totalBreakTimeMillis;
        public int sessionCount;

        public long getNetStudyTime() {
            return totalTimeMillis - totalBreakTimeMillis;
        }
        
        public double getFocusScore() {
            if (totalTimeMillis == 0) return 0;
            return (double) getNetStudyTime() / totalTimeMillis * 100;
        }
    }

    /**
     * Aggregates stats by Subject ID.
     */
    public static Map<Integer, StudyStats> aggregateBySubject(List<StudySession> sessions) {
        Map<Integer, StudyStats> statsMap = new HashMap<>();
        for (StudySession session : sessions) {
            StudyStats stats = statsMap.getOrDefault(session.subjectId, new StudyStats());
            stats.totalTimeMillis += (session.endTimestamp - session.startTimestamp);
            stats.totalBreakTimeMillis += session.breakDurationMillis;
            stats.totalDistractions += session.distractionCount;
            stats.sessionCount++;
            statsMap.put(session.subjectId, stats);
        }
        return statsMap;
    }

    /**
     * Filters sessions within a specific time range.
     */
    public static List<StudySession> filterSessionsInRange(List<StudySession> sessions, long fromTimestamp) {
        List<StudySession> filtered = new ArrayList<>();
        for (StudySession session : sessions) {
            if (session.startTimestamp >= fromTimestamp) {
                filtered.add(session);
            }
        }
        return filtered;
    }
    
    public static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        return String.format("%dh %dm", hours, minutes % 60);
    }
}
