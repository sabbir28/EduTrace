package org.sabbir.edutrace.data.repository;
import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.db.StudyDao;
import org.sabbir.edutrace.data.db.StudyDatabase;
import org.sabbir.edutrace.data.models.Degree;
import org.sabbir.edutrace.data.models.Subject;
import org.sabbir.edutrace.data.models.StudySession;

import android.app.Application;
import androidx.lifecycle.LiveData;
import java.util.List;

public class StudyRepository {
    private StudyDao mStudyDao;
    private LiveData<List<Degree>> mAllDegrees;
    private LiveData<List<StudySession>> mAllSessions;

    public StudyRepository(Application application) {
        StudyDatabase db = StudyDatabase.getDatabase(application);
        mStudyDao = db.studyDao();
        mAllDegrees = mStudyDao.getAllDegrees();
        mAllSessions = mStudyDao.getAllSessions();
    }

    public LiveData<List<Degree>> getAllDegrees() {
        return mAllDegrees;
    }

    public LiveData<List<StudySession>> getAllSessions() {
        return mAllSessions;
    }

    public void insertDegree(Degree degree) {
        StudyDatabase.databaseWriteExecutor.execute(() -> {
            mStudyDao.insertDegree(degree);
        });
    }

    public void insertSubject(Subject subject) {
        StudyDatabase.databaseWriteExecutor.execute(() -> {
            mStudyDao.insertSubject(subject);
        });
    }

    public void insertSession(StudySession session) {
        StudyDatabase.databaseWriteExecutor.execute(() -> {
            mStudyDao.insertSession(session);
        });
    }

    public void updateDegree(Degree degree) {
        StudyDatabase.databaseWriteExecutor.execute(() -> {
            mStudyDao.updateDegree(degree);
        });
    }

    public void deleteDegree(Degree degree) {
        StudyDatabase.databaseWriteExecutor.execute(() -> {
            mStudyDao.deleteDegree(degree);
        });
    }

    public void updateSubject(Subject subject) {
        StudyDatabase.databaseWriteExecutor.execute(() -> {
            mStudyDao.updateSubject(subject);
        });
    }

    public void deleteSubject(Subject subject) {
        StudyDatabase.databaseWriteExecutor.execute(() -> {
            mStudyDao.deleteSubject(subject);
        });
    }

    public LiveData<List<Subject>> getSubjectsForDegree(int degreeId) {
        return mStudyDao.getSubjectsForDegree(degreeId);
    }

    public LiveData<List<Subject>> getAllSubjects() {
        return mStudyDao.getAllSubjects();
    }
}
