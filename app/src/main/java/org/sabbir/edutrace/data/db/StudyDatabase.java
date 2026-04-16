package org.sabbir.edutrace.data.db;
import org.sabbir.edutrace.R;
import org.sabbir.edutrace.data.models.Degree;
import org.sabbir.edutrace.data.models.Subject;
import org.sabbir.edutrace.data.models.StudySession;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Degree.class, Subject.class, StudySession.class}, version = 1, exportSchema = false)
public abstract class StudyDatabase extends RoomDatabase {
    public abstract StudyDao studyDao();

    private static volatile StudyDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static StudyDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (StudyDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    StudyDatabase.class, "study_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
