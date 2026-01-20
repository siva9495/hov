package com.siva.homeofveltech.Model;

public class TimetableItem {
    public final String code;
    public final String subject;
    public final String time;
    public final String status;

    public TimetableItem(String code, String subject, String time, String status) {
        this.code = code == null ? "" : code.trim();
        this.subject = subject == null ? "" : subject.trim();
        this.time = time == null ? "" : time.trim();
        this.status = status == null ? "" : status.trim();
    }
}
