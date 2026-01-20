package com.siva.homeofveltech.Model;

public class StudentProfile {
    public final String studentName;
    public final String branch;

    public StudentProfile(String studentName, String branch) {
        this.studentName = studentName == null ? "" : studentName.trim();
        this.branch = branch == null ? "" : branch.trim();
    }
}
