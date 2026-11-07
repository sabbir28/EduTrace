package org.sabbir.edutrace.desktop.data;

public class DesktopSubject {
    private int id;
    private int degreeId;
    private String name;
    private String colorHex;

    public DesktopSubject() {}

    public DesktopSubject(int id, int degreeId, String name, String colorHex) {
        this.id = id;
        this.degreeId = degreeId;
        this.name = name;
        this.colorHex = colorHex != null ? colorHex : "#FACC15";
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getDegreeId() { return degreeId; }
    public void setDegreeId(int degreeId) { this.degreeId = degreeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    @Override
    public String toString() {
        return name;
    }
}
