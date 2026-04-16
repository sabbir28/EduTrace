package org.sabbir.edutrace.viewmodels;
import org.sabbir.edutrace.R;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import java.util.List;
import org.sabbir.edutrace.data.models.Degree;
import org.sabbir.edutrace.data.repository.StudyRepository;
import org.sabbir.edutrace.data.models.Subject;
import org.sabbir.edutrace.data.models.StudySession;

public class StudyViewModel extends AndroidViewModel {
    private StudyRepository mRepository;
    private LiveData<List<Degree>> mAllDegrees;
    private LiveData<List<StudySession>> mAllSessions;

    public StudyViewModel(Application application) {
        super(application);
        mRepository = new StudyRepository(application);
        mAllDegrees = mRepository.getAllDegrees();
        mAllSessions = mRepository.getAllSessions();
    }

    public LiveData<List<Degree>> getAllDegrees() {
        return mAllDegrees;
    }

    public LiveData<List<StudySession>> getAllSessions() {
        return mAllSessions;
    }

    public void insertDegree(Degree degree) {
        mRepository.insertDegree(degree);
    }

    public void insertSubject(Subject subject) {
        mRepository.insertSubject(subject);
    }

    public void insertSession(StudySession session) {
        mRepository.insertSession(session);
    }

    public void updateDegree(Degree degree) {
        mRepository.updateDegree(degree);
    }

    public void deleteDegree(Degree degree) {
        mRepository.deleteDegree(degree);
    }

    public void updateSubject(Subject subject) {
        mRepository.updateSubject(subject);
    }

    public void deleteSubject(Subject subject) {
        mRepository.deleteSubject(subject);
    }

    public LiveData<List<Subject>> getSubjectsForDegree(int degreeId) {
        return mRepository.getSubjectsForDegree(degreeId);
    }

    public LiveData<List<Subject>> getAllSubjects() {
        return mRepository.getAllSubjects();
    }
}
