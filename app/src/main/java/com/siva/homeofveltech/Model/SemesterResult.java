package com.siva.homeofveltech.Model;

import java.util.List;

public class SemesterResult {
    public String semesterName;
    public double tgpa; // out of 10
    public List<SubjectGrade> subjects;

    public SemesterResult(String semesterName, double tgpa, List<SubjectGrade> subjects) {
        this.semesterName = semesterName;
        this.tgpa = tgpa;
        this.subjects = subjects;
    }
}

