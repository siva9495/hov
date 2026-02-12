package com.siva.homeofveltech.Model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudentDashboardData {

    public final String studentName;
    public final String branch;
    public final double overallAttendancePercent; // -1 if not found
    public final double overallGpa;
    public final Map<String, List<TimetableItem>> weekTimetable;

    public StudentDashboardData(String studentName, String branch,
                                double overallAttendancePercent,
                                double overallGpa,
                                Map<String, List<TimetableItem>> weekTimetable) {

        this.studentName = studentName == null ? "" : studentName.trim();
        this.branch = branch == null ? "" : branch.trim();
        this.overallAttendancePercent = overallAttendancePercent;
        this.overallGpa = overallGpa;
        this.weekTimetable = weekTimetable == null ? new HashMap<>() : weekTimetable;
    }

    public List<TimetableItem> getTimetableForDay(String dayName) {
        if (dayName == null) return new java.util.ArrayList<>();
        List<TimetableItem> list = weekTimetable.get(dayName.trim());
        return list == null ? new java.util.ArrayList<>() : list;
    }
}
