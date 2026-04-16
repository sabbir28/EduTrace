package org.sabbir.edutrace.data.models;
import org.sabbir.edutrace.R;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "degrees")
public class Degree {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String name;
    public String colorHex;

    public Degree(String name, String colorHex) {
        this.name = name;
        this.colorHex = colorHex;
    }
}
