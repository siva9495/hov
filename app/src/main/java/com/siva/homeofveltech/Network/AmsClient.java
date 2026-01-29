package com.siva.homeofveltech.Network;

import com.siva.homeofveltech.Model.PeriodAttendanceItem;
import com.siva.homeofveltech.Model.StudentDashboardData;
import com.siva.homeofveltech.Model.StudentProfile;
import com.siva.homeofveltech.Model.SubjectAttendanceItem;
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
    private static final String ATTENDANCE_URL = BASE + "Attendance.aspx";

    private final OkHttpClient client;
    private String username;
    private String password;


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
        this.username = username;
        this.password = password;
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
            String html = get(ATTENDANCE_URL);
            return !(html.contains("STUDENT LOGIN") || html.contains("txtUserName"));
        } catch (Exception e) {
            return false;
        }
    }

    private void renewSession() throws IOException {
        if (username == null || password == null) {
            throw new IOException("Credentials not available for session renewal.");
        }
        login(username, password);
    }


    // -------------------- PROFILE --------------------

    public StudentProfile fetchStudentProfile() throws IOException {
        if (!isSessionValid()) {
            renewSession();
        }
        String html = get(ATTENDANCE_URL);
        Document doc = Jsoup.parse(html);

        String studentName = textOrEmpty(doc.selectFirst("#MainContent_lblName"));
        String branch = textOrEmpty(doc.selectFirst("#MainContent_lblBranch"));

        return new StudentProfile(studentName, branch);
    }

    // -------------------- ATTENDANCE (SUMMARY GRID) --------------------

    public List<SubjectAttendanceItem> fetchAttendanceData() throws IOException {
        if (!isSessionValid()) {
            renewSession();
        }
        String html = get(ATTENDANCE_URL);
        Document doc = Jsoup.parse(html);

        List<SubjectAttendanceItem> attendanceList = new ArrayList<>();
        Elements rows = doc.select("#MainContent_GridView4 tr");

        for (int i = 1; i < rows.size(); i++) { // Skip header row
            Element row = rows.get(i);
            Elements cols = row.select("td");

            if (cols.size() >= 13) {
                String subjectName = cols.get(2).text();
                String subjectCode = cols.get(1).text();
                String facultyName = cols.get(12).text();
                int totalSessions = safeInt(cols.get(4).text());
                int conductedSessions = safeInt(cols.get(5).text());
                int attendedSessions = safeInt(cols.get(6).text());
                int absent = safeInt(cols.get(7).text());
                int presentPercentage = safeInt(cols.get(8).text());
                int overallPercentage = safeInt(cols.get(9).text());

                attendanceList.add(new SubjectAttendanceItem(
                        subjectName,
                        subjectCode,
                        facultyName,
                        totalSessions,
                        attendedSessions,
                        conductedSessions,
                        absent,
                        presentPercentage,
                        overallPercentage
                ));
            }
        }

        return attendanceList;
    }

    // -------------------- SUBJECT FULL ATTENDANCE (FIXED) --------------------

    /**
     * Fetches detailed period-wise attendance for a specific subject.
     * Fixes 500 error by:
     * 1) Sending valid Month dropdown value only if options exist
     * 2) Parsing ASP.NET AJAX Delta response by extracting UpdatePanel HTML
     */
    public List<PeriodAttendanceItem> fetchSubjectFullAttendance(String subjectCode, String subjectName) throws IOException {
        if (!isSessionValid()) {
            renewSession();
        }
        // 1) GET Attendance.aspx
        String html = get(ATTENDANCE_URL);
        Document doc = Jsoup.parse(html);

        // 2) Find exact course value from dropdown (must match server option)
        Element courseDropdown = doc.selectFirst("#MainContent_Courselist");
        String exactCourseValue = null;

        if (courseDropdown != null) {
            for (Element option : courseDropdown.select("option")) {
                String value = option.attr("value");
                if (value != null && value.startsWith(subjectCode + "-")) {
                    exactCourseValue = value;
                    break;
                }
            }
        }

        if (exactCourseValue == null) return new ArrayList<>();

        // 3) Year (pick first real one)
        String selectedYear = null;
        Element yearDropdown = doc.selectFirst("#MainContent_DropDownList2");
        if (yearDropdown != null) {
            for (Element option : yearDropdown.select("option")) {
                String val = option.attr("value");
                if (val != null && !val.equals("Select Year") && !val.equals("0") && !val.trim().isEmpty()) {
                    selectedYear = val;
                    break;
                }
            }
        }
        if (selectedYear == null) selectedYear = "2025"; // fallback

        // 4) Month (ONLY send if it has options!)
        String monthVal = getFirstOrSelectedOptionValue(doc, "MainContent_DropDownList1");
        // monthVal may be null (no options) -> DO NOT SEND

        // 5) Build AJAX POST correctly
        FormBody.Builder fb = new FormBody.Builder();

        // ScriptManager must match the trigger control
        fb.add("ctl00$MainContent$ScriptManager1",
                "ctl00$MainContent$UpdatePanel6|ctl00$MainContent$Button1");

        Element textBox1 = doc.selectFirst("#MainContent_TextBox1");
        if (textBox1 != null) {
            fb.add("ctl00$MainContent$TextBox1", textBox1.attr("value"));
        }

        fb.add("ctl00$MainContent$Courselist", exactCourseValue);
        fb.add("ctl00$MainContent$DropDownList2", selectedYear);

        if (monthVal != null && !monthVal.trim().isEmpty()) {
            fb.add("ctl00$MainContent$DropDownList1", monthVal);
        }

        // radio (same as your existing usage)
        fb.add("ctl00$MainContent$g1", "RadioButton3");

        // event fields (must exist)
        fb.add("__EVENTTARGET", "");
        fb.add("__EVENTARGUMENT", "");
        fb.add("__LASTFOCUS", "");

        // hidden fields (skip duplicates)
        for (Element input : doc.select("input[type=hidden][name]")) {
            String name = input.attr("name");
            if ("__EVENTTARGET".equals(name) || "__EVENTARGUMENT".equals(name) || "__LASTFOCUS".equals(name)) {
                continue;
            }
            fb.add(name, input.attr("value") == null ? "" : input.attr("value"));
        }

        fb.add("__ASYNCPOST", "true");
        fb.add("ctl00$MainContent$Button1", "Coursewise Attendance");

        Request post = new Request.Builder()
                .url(ATTENDANCE_URL)
                .post(fb.build())
                .header("Referer", ATTENDANCE_URL)
                .header("Origin", BASE)
                .header("User-Agent", "Mozilla/5.0")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-MicrosoftAjax", "Delta=true")
                .build();

        String delta;
        try (Response res = client.newCall(post).execute()) {
            if (!res.isSuccessful() || res.body() == null) {
                throw new IOException("POST failed: " + res.code());
            }
            delta = res.body().string();
        }

        // 6) Extract GridView1 HTML from UpdatePanel6 and parse
        String panelHtml = extractUpdatePanelHtml(delta, "MainContent_UpdatePanel6");
        if (panelHtml == null) return new ArrayList<>();

        return parseSubjectAttendanceResponseFromPanel(panelHtml, subjectName);
    }

    private List<PeriodAttendanceItem> parseSubjectAttendanceResponseFromPanel(String panelHtml, String courseName) {
        List<PeriodAttendanceItem> periods = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(panelHtml);

            Element table = doc.getElementById("MainContent_GridView1");
            if (table == null) return periods;

            Elements rows = table.select("tr");
            for (int i = 1; i < rows.size(); i++) {
                Elements cols = rows.get(i).select("td");
                if (cols.size() >= 7) {
                    String date = formatDate(cols.get(4).text().trim());
                    String timeSlot = cols.get(5).text().trim();
                    String status = cols.get(6).text().trim();
                    boolean isPresent = status.equalsIgnoreCase("P");

                    periods.add(new PeriodAttendanceItem(courseName, date, timeSlot, isPresent));
                }
            }
        } catch (Exception ignored) {}

        return periods;
    }

    // -------------------- DASHBOARD DATA --------------------

    public StudentDashboardData fetchStudentDashboardData() throws Exception {
        if (!isSessionValid()) {
            renewSession();
        }
        String html = get(ATTENDANCE_URL);
        Document doc = Jsoup.parse(html);

        String studentName = textOrEmpty(doc.selectFirst("#MainContent_lblName"));
        String branch      = textOrEmpty(doc.selectFirst("#MainContent_lblBranch"));

        // courseName -> courseCode map (robust)
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

                        // show course code if available, else fallback to subject name
                        String code = nameToCode.get(key(subjectName));
                        if (code == null || code.trim().isEmpty()) {
                            code = subjectName;
                        }

                        String status = computeStatusIfToday(day, time);

                        // Keep subjectName in model; UI can decide what to show
                        dayItems.add(new TimetableItem(code, subjectName, time, status));
                    }

                    timetableByDay.put(day, dayItems);
                }
            }
        }

        // overall attendance (Present / Faculty Sessions)
        double overall = parseOverallAttendance(doc);

        return new StudentDashboardData(studentName, branch, overall, timetableByDay);
    }

    // -------------------- Attendance % parser --------------------

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

                // best denom: Faculty Sessions (classes conducted so far)
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

    // -------------------- AJAX helpers --------------------

    private static String extractUpdatePanelHtml(String delta, String panelId) {
        String[] parts = delta.split("\\|");
        for (int i = 0; i < parts.length - 2; i++) {
            if ("updatePanel".equals(parts[i]) && panelId.equals(parts[i + 1])) {
                return parts[i + 2];
            }
        }
        return null;
    }

    private static Map<String, String> extractHiddenFieldsFromDelta(String delta) {
        Map<String, String> map = new HashMap<>();
        String[] parts = delta.split("\\|");
        for (int i = 0; i < parts.length - 2; i++) {
            if ("hiddenField".equals(parts[i])) {
                map.put(parts[i + 1], parts[i + 2]);
            }
        }
        return map;
    }

    private static String getFirstOrSelectedOptionValue(Document doc, String selectId) {
        Element sel = doc.getElementById(selectId);
        if (sel == null) return null;

        Element selected = sel.selectFirst("option[selected]");
        if (selected != null) return selected.attr("value");

        Element first = sel.selectFirst("option");
        if (first != null) return first.attr("value");

        return null; // no options
    }

    // -------------------- Small utils --------------------

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return "";
        if (dateStr.contains(" ")) return dateStr.split(" ")[0];
        return dateStr;
    }

    private static String key(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
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
