package org.sabbir.edutrace.data.models;
import org.sabbir.edutrace.R;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "study_sessions",
    foreignKeys = @ForeignKey(
        entity = Subject.class,
        parentColumns = "id",
        childColumns = "subjectId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("subjectId")}
)
public class StudySession {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public int subjectId;
    public long startTimestamp;
    public long endTimestamp;
    public long breakDurationMillis;
    public int distractionCount;
    public String notes;

    public StudySession(int subjectId, long startTimestamp, long endTimestamp, long breakDurationMillis, int distractionCount, String notes) {
        this.subjectId = subjectId;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.breakDurationMillis = breakDurationMillis;
        this.distractionCount = distractionCount;
        this.notes = notes;
    }
}
