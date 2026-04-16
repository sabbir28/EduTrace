package org.sabbir.edutrace.data.models;
import org.sabbir.edutrace.R;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "subjects",
    foreignKeys = @ForeignKey(
        entity = Degree.class,
        parentColumns = "id",
        childColumns = "degreeId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("degreeId")}
)
public class Subject {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public int degreeId;
    public String name;
    public String colorHex;

    public Subject(int degreeId, String name, String colorHex) {
        this.degreeId = degreeId;
        this.name = name;
        this.colorHex = colorHex;
    }
}
