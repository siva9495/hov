package com.siva.homeofveltech.Model;

public class PeriodAttendanceItem {
    public String courseName;
    public String date;      // e.g., 12-12-2025
    public String timeSlot;  // e.g., 10:00 - 11:00AM
    public boolean present;  // true => P, false => A

    public PeriodAttendanceItem(String courseName, String date, String timeSlot, boolean present) {
        this.courseName = courseName;
        this.date = date;
        this.timeSlot = timeSlot;
        this.present = present;
    }
}
