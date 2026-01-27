package com.siva.homeofveltech.Model;

public class SubjectAttendanceItem {
    public String subjectName;
    public String subjectCode;
    public String facultyName;
    public int totalSessions;
    public int attendedSessions;
    public int conductedSessions;
    public int absent;
    public int presentPercentage;
    public int overallPercentage;

    public SubjectAttendanceItem(String subjectName, String subjectCode, String facultyName, int totalSessions, int attendedSessions, int conductedSessions, int absent, int presentPercentage, int overallPercentage) {
        this.subjectName = subjectName;
        this.subjectCode = subjectCode;
        this.facultyName = facultyName;
        this.totalSessions = totalSessions;
        this.attendedSessions = attendedSessions;
        this.conductedSessions = conductedSessions;
        this.absent = absent;
        this.presentPercentage = presentPercentage;
        this.overallPercentage = overallPercentage;
    }
}
