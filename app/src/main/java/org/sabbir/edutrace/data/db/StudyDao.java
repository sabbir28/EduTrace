package org.sabbir.edutrace.data.db;
import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.models.Degree;
import org.sabbir.edutrace.data.models.Subject;
import org.sabbir.edutrace.data.models.StudySession;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface StudyDao {
    @Insert
    void insertDegree(Degree degree);

    @Insert
    void insertSubject(Subject subject);

    @Insert
    void insertSession(StudySession session);

    @Update
    void updateDegree(Degree degree);

    @Delete
    void deleteDegree(Degree degree);

    @Update
    void updateSubject(Subject subject);

    @Delete
    void deleteSubject(Subject subject);

    @Query("SELECT * FROM degrees")
    LiveData<List<Degree>> getAllDegrees();

    @Query("SELECT * FROM subjects")
    LiveData<List<Subject>> getAllSubjects();

    @Query("SELECT * FROM subjects WHERE degreeId = :degreeId")
    LiveData<List<Subject>> getSubjectsForDegree(int degreeId);

    @Query("SELECT * FROM study_sessions WHERE subjectId = :subjectId")
    LiveData<List<StudySession>> getSessionsForSubject(int subjectId);

    @Query("SELECT * FROM study_sessions")
    LiveData<List<StudySession>> getAllSessions();

    @Query("SELECT SUM(endTimestamp - startTimestamp - breakDurationMillis) FROM study_sessions WHERE subjectId = :subjectId")
    LiveData<Long> getTotalTimeForSubject(int subjectId);

    // Get stats for a specific range (for 7-day and monthly reports)
    @Query("SELECT * FROM study_sessions WHERE startTimestamp >= :fromTimestamp")
    LiveData<List<StudySession>> getSessionsFrom(long fromTimestamp);

    @Query("SELECT SUM(endTimestamp - startTimestamp - breakDurationMillis) FROM study_sessions WHERE startTimestamp >= :fromTimestamp")
    long getTotalTimeFromSync(long fromTimestamp);
}
