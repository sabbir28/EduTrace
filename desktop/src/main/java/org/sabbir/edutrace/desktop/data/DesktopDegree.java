package org.sabbir.edutrace.desktop.data;

public class DesktopDegree {
    private int id;
    private String name;
    private String colorHex;

    public DesktopDegree() {}

    public DesktopDegree(int id, String name, String colorHex) {
        this.id = id;
        this.name = name;
        this.colorHex = colorHex != null ? colorHex : "#FFB800";
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }

    @Override
    public String toString() {
        return name;
    }
}

