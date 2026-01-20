package com.siva.homeofveltech.Network;

import com.siva.homeofveltech.Model.StudentDashboardData;
import com.siva.homeofveltech.Model.StudentProfile;
import com.siva.homeofveltech.Model.TimetableItem;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AmsClient {

    private static final String BASE = "https://ams.veltech.edu.in/";
    private static final String LOGIN_URL = BASE + "index.aspx";

    private final OkHttpClient client;

    public AmsClient() {
        this.client = OkHttpProvider.getClient();
    }

    public AmsClient(OkHttpClient client) {
        this.client = client;
    }

    // -------------------- LOGIN --------------------

    private Map<String, String> fetchHiddenFields() throws IOException {
        String html = get(LOGIN_URL);
        Document doc = Jsoup.parse(html);

        Map<String, String> fields = new HashMap<>();
        for (Element input : doc.select("input[type=hidden][name]")) {
            fields.put(input.attr("name"), input.attr("value"));
        }

        if (!fields.containsKey("__VIEWSTATE") || !fields.containsKey("__EVENTVALIDATION")) {
            throw new IOException("ASP.NET hidden fields not found on login page.");
        }
        return fields;
    }

    /** Returns true if login success, false if invalid credentials */
    public boolean login(String username, String password) throws IOException {
        Map<String, String> hidden = fetchHiddenFields();

        FormBody.Builder fb = new FormBody.Builder();
        for (Map.Entry<String, String> e : hidden.entrySet()) {
            fb.add(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }

        fb.add("txtUserName", username);
        fb.add("txtPassword", password);
        fb.add("Button1", "LET'S GO");

        Request post = new Request.Builder()
                .url(LOGIN_URL)
                .post(fb.build())
                .header("Referer", LOGIN_URL)
                .header("Origin", BASE)
                .header("User-Agent", "Mozilla/5.0")
                .build();

        try (Response res = client.newCall(post).execute()) {
            if (!res.isSuccessful()) throw new IOException("POST login failed: " + res.code());
        }

        // Best verification: check secured page after POST
        return isSessionValid();
    }

    /** Checks if session is still valid */
    public boolean isSessionValid() {
        try {
            String html = get(BASE + "Attendance.aspx");
            return !(html.contains("STUDENT LOGIN") || html.contains("txtUserName"));
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------- PROFILE --------------------

    public StudentProfile fetchStudentProfile() throws IOException {
        String html = get(BASE + "Attendance.aspx");
        Document doc = Jsoup.parse(html);

        String studentName = textOrEmpty(doc.selectFirst("#MainContent_lblName"));
        String branch = textOrEmpty(doc.selectFirst("#MainContent_lblBranch"));

        return new StudentProfile(studentName, branch);
    }

    private static String key(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
    }

    // -------------------- DASHBOARD DATA --------------------
    public StudentDashboardData fetchStudentDashboardData() throws Exception {

        String html = get(BASE + "Attendance.aspx");
        Document doc = Jsoup.parse(html);

        String studentName = textOrEmpty(doc.selectFirst("#MainContent_lblName"));
        String branch      = textOrEmpty(doc.selectFirst("#MainContent_lblBranch"));

        // ✅ courseName -> courseCode (robust map)
        Map<String, String> nameToCode = new HashMap<>();
        Element courseTable = doc.getElementById("MainContent_GridView3");
        if (courseTable != null) {
            Elements rows = courseTable.select("tr");
            for (int i = 1; i < rows.size(); i++) {
                Elements tds = rows.get(i).select("td");
                if (tds.size() >= 4) {
                    String code = tds.get(2).text().trim();
                    String name = tds.get(3).text().trim();
                    if (!name.isEmpty() && !code.isEmpty()) {
                        nameToCode.put(key(name), code);
                    }
                }
            }
        }

        // timetable (week)
        Map<String, List<TimetableItem>> timetableByDay = new HashMap<>();
        Element tt = doc.getElementById("MainContent_GridTimetable");
        List<String> slots = new ArrayList<>();

        if (tt != null) {
            Elements rows = tt.select("tr");
            if (!rows.isEmpty()) {

                // header slots
                Elements ths = rows.get(0).select("th");
                for (int i = 1; i < ths.size(); i++) {
                    slots.add(ths.get(i).text().trim());
                }

                // day rows
                for (int r = 1; r < rows.size(); r++) {
                    Elements tds = rows.get(r).select("td");
                    if (tds.size() < 2) continue;

                    String day = normalizeDayName(tds.get(0).text().trim());
                    List<TimetableItem> dayItems = new ArrayList<>();

                    for (int c = 1; c < tds.size() && (c - 1) < slots.size(); c++) {
                        String subjectName = tds.get(c).text().trim();
                        if (subjectName.isEmpty()
                                || "-".equals(subjectName)
                                || "Break".equalsIgnoreCase(subjectName)) continue;

                        String time = slots.get(c - 1);

                        // ✅ show course code (subject id)
                        String code = nameToCode.get(key(subjectName));
                        if (code == null || code.trim().isEmpty()) {
                            // fallback: show shortened subject name if code missing
                            code = subjectName;
                        }

                        String status = computeStatusIfToday(day, time);

                        // NOTE: We keep subjectName in model (optional), UI will show only code+time+status
                        dayItems.add(new TimetableItem(code, subjectName, time, status));
                    }

                    timetableByDay.put(day, dayItems);
                }
            }
        }

        // ✅ overall attendance (Present / Faculty Sessions)
        double overall = parseOverallAttendance(doc);

        return new StudentDashboardData(studentName, branch, overall, timetableByDay);
    }

    // -------------------- Attendance % parser (FIXED) --------------------
    private double parseOverallAttendance(Document doc) {
        try {
            Element table = doc.getElementById("MainContent_GridView4");
            if (table == null) return -1;

            Elements rows = table.select("tr");
            if (rows.size() < 2) return -1;

            Elements header = rows.get(0).select("th");

            int facultyIdx = -1;
            int presentIdx = -1;
            int absentIdx  = -1;
            int totalIdx   = -1;

            for (int i = 0; i < header.size(); i++) {
                String h = header.get(i).text().trim().toLowerCase(Locale.US);
                if (h.contains("faculty sessions")) facultyIdx = i;
                if (h.equals("present")) presentIdx = i;
                if (h.equals("absent")) absentIdx = i;
                if (h.contains("total sessions")) totalIdx = i;
            }

            // fallback to known AMS positions
            if (totalIdx == -1) totalIdx = 4;
            if (facultyIdx == -1) facultyIdx = 5;
            if (presentIdx == -1) presentIdx = 6;
            if (absentIdx == -1) absentIdx = 7;

            int sumPresent = 0;
            int sumDenom = 0; // denom = faculty sessions (preferred)

            for (int r = 1; r < rows.size(); r++) {
                Elements tds = rows.get(r).select("td");
                if (tds.size() <= Math.max(Math.max(totalIdx, facultyIdx), presentIdx)) continue;

                int present = safeInt(tds.get(presentIdx).text());
                int faculty = safeInt(tds.get(facultyIdx).text());
                int absent  = (tds.size() > absentIdx) ? safeInt(tds.get(absentIdx).text()) : 0;
                int total   = safeInt(tds.get(totalIdx).text());

                // ✅ best denom: Faculty Sessions (classes conducted so far)
                int denom = faculty;

                // fallback if faculty is missing but present+absent exists
                if (denom <= 0) {
                    int pa = present + absent;
                    if (pa > 0) denom = pa;
                }

                // last fallback: total sessions
                if (denom <= 0) denom = total;

                if (denom > 0) {
                    sumPresent += present;
                    sumDenom += denom;
                }
            }

            if (sumDenom == 0) return -1;
            return (sumPresent * 100.0) / sumDenom;

        } catch (Exception e) {
            return -1;
        }
    }


    // -------------------- Status logic --------------------

    private String computeStatusIfToday(String day, String timeRange) {
        if (day == null || timeRange == null) return "Upcoming";

        String today = new SimpleDateFormat("EEEE", Locale.US).format(new Date());
        today = normalizeDayName(today);

        if (!day.equalsIgnoreCase(today)) return "Upcoming";

        int[] range = parseSlotMinutes(timeRange);
        if (range == null) return "Upcoming";

        Calendar cal = Calendar.getInstance();
        int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        int start = range[0];
        int end = range[1];

        if (now < start) return "Upcoming";
        if (now >= start && now <= end) return "On Going";
        return "Completed";
    }

    // Parses: "8-8.50 AM", "12.45-1.35 PM"
    private int[] parseSlotMinutes(String slot) {
        try {
            String s = slot.trim().replace(" ", "");

            Pattern p = Pattern.compile(
                    "([0-9]{1,2}(?:\\.[0-9]{1,2})?)-([0-9]{1,2}(?:\\.[0-9]{1,2})?)(AM|PM)",
                    Pattern.CASE_INSENSITIVE
            );

            Matcher m = p.matcher(s);
            if (!m.find()) return null;

            String startStr = m.group(1);
            String endStr = m.group(2);
            String ap = m.group(3).toUpperCase(Locale.US);

            int start = hmToMinutes(startStr, ap);
            int end = hmToMinutes(endStr, ap);

            if (end < start) end += 12 * 60;

            return new int[]{start, end};
        } catch (Exception e) {
            return null;
        }
    }

    private int hmToMinutes(String hDotM, String ampm) {
        String[] parts = hDotM.split("\\.");
        int hour = safeInt(parts[0]);
        int min = 0;

        if (parts.length > 1) {
            String mm = parts[1];
            if (mm.length() == 1) mm = mm + "0";
            min = safeInt(mm);
        }

        if ("AM".equals(ampm)) {
            if (hour == 12) hour = 0;
        } else {
            if (hour != 12) hour += 12;
        }

        return hour * 60 + min;
    }

    private static int safeInt(String s) {
        try {
            if (s == null) return 0;
            String digits = s.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return 0;
            return Integer.parseInt(digits);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String normalizeDayName(String raw) {
        if (raw == null) return "";
        String d = raw.trim();

        // Handle common short forms if AMS ever returns them
        if (d.equalsIgnoreCase("Mon")) return "Monday";
        if (d.equalsIgnoreCase("Tue")) return "Tuesday";
        if (d.equalsIgnoreCase("Wed")) return "Wednesday";
        if (d.equalsIgnoreCase("Thu")) return "Thursday";
        if (d.equalsIgnoreCase("Fri")) return "Friday";
        if (d.equalsIgnoreCase("Sat")) return "Saturday";
        if (d.equalsIgnoreCase("Sun")) return "Sunday";

        return d;
    }

    // -------------------- HTTP --------------------

    private String get(String url) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Mozilla/5.0")
                .build();

        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful() || res.body() == null) {
                throw new IOException("HTTP " + res.code() + " for " + url);
            }
            return res.body().string();
        }
    }

    private static String textOrEmpty(Element e) {
        return e == null ? "" : e.text().trim();
    }

    // Used by StudentDashboardActivity spinner default selection
    public static String getTodayWeekdayNameStatic() {
        return new SimpleDateFormat("EEEE", Locale.US).format(new Date());
    }
}
