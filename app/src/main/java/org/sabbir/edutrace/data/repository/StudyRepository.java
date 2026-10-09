package org.sabbir.edutrace.data.repository;
import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.db.StudyDao;
import org.sabbir.edutrace.data.db.StudyDatabase;
import org.sabbir.edutrace.data.models.Degree;
import org.sabbir.edutrace.data.models.Subject;
import org.sabbir.edutrace.data.models.StudySession;

import android.app.Application;
import android.content.Context;
import androidx.lifecycle.LiveData;
import java.util.List;

public class StudyRepository {
    private Context mContext;
    private StudyDao mStudyDao;
    private LiveData<List<Degree>> mAllDegrees;
    private LiveData<List<StudySession>> mAllSessions;

    public StudyRepository(Context context) {
        mContext = context != null ? context.getApplicationContext() : null;
        StudyDatabase db = StudyDatabase.getDatabase(context);
        mStudyDao = db.studyDao();
        mAllDegrees = mStudyDao.getAllDegrees();
        mAllSessions = mStudyDao.getAllSessions();
    }

    public StudyRepository(Application application) {
        this((Context) application);
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
            if (mContext != null) org.sabbir.edutrace.utils.LocalVaultManager.autoSaveVault(mContext);
        });
    }

    public void insertSubject(Subject subject) {
        StudyDatabase.databaseWriteExecutor.execute(() -> {
            mStudyDao.insertSubject(subject);
            if (mContext != null) org.sabbir.edutrace.utils.LocalVaultManager.autoSaveVault(mContext);
        });
    }

    public void insertSession(StudySession session) {
        StudyDatabase.databaseWriteExecutor.execute(() -> {
            mStudyDao.insertSession(session);
            if (mContext != null) org.sabbir.edutrace.utils.LocalVaultManager.autoSaveVault(mContext);
        });
    }

    public void updateDegree(Degree degree) {
        StudyDatabase.databaseWriteExecutor.execute(() -> {
            mStudyDao.updateDegree(degree);
            if (mContext != null) org.sabbir.edutrace.utils.LocalVaultManager.autoSaveVault(mContext);
        });
    }

    public void deleteDegree(Degree degree) {
        StudyDatabase.databaseWriteExecutor.execute(() -> {
            mStudyDao.deleteDegree(degree);
            if (mContext != null) org.sabbir.edutrace.utils.LocalVaultManager.autoSaveVault(mContext);
        });
    }

    public void updateSubject(Subject subject) {
        StudyDatabase.databaseWriteExecutor.execute(() -> {
            mStudyDao.updateSubject(subject);
            if (mContext != null) org.sabbir.edutrace.utils.LocalVaultManager.autoSaveVault(mContext);
        });
    }

    public void deleteSubject(Subject subject) {
        StudyDatabase.databaseWriteExecutor.execute(() -> {
            mStudyDao.deleteSubject(subject);
            if (mContext != null) org.sabbir.edutrace.utils.LocalVaultManager.autoSaveVault(mContext);
        });
    }

    public LiveData<List<Subject>> getSubjectsForDegree(int degreeId) {
        return mStudyDao.getSubjectsForDegree(degreeId);
    }

    public LiveData<List<Subject>> getAllSubjects() {
        return mStudyDao.getAllSubjects();
    }
}
