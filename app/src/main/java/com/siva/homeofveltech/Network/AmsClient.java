package com.siva.homeofveltech.Network;

import com.siva.homeofveltech.Model.PeriodAttendanceItem;
import com.siva.homeofveltech.Model.SemesterResult;
import com.siva.homeofveltech.Model.StudentDashboardData;
import com.siva.homeofveltech.Model.StudentProfile;
import com.siva.homeofveltech.Model.SubjectAttendanceItem;
import com.siva.homeofveltech.Model.SubjectGrade;
import com.siva.homeofveltech.Model.TimetableItem;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AmsClient {

    private static final String BASE = "https://ams.veltech.edu.in/";
    private static final String LOGIN_URL = BASE + "index.aspx";
    private static final String CAPTCHA_URL = BASE + "Captcha.aspx";
    private static final String ATTENDANCE_URL = BASE + "Attendance.aspx";
    private static final String SEMESTER_MARK_URL = BASE + "SemesterMark.aspx";

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

    public static class LoginPageData {
        public final Map<String, String> hiddenFields;
        public final byte[] captchaImage;

        LoginPageData(Map<String, String> hiddenFields, byte[] captchaImage) {
            this.hiddenFields = hiddenFields;
            this.captchaImage = captchaImage;
        }
    }

    public LoginPageData fetchLoginPage() throws IOException {
        String html = get(LOGIN_URL);
        Document doc = Jsoup.parse(html);

        Map<String, String> fields = new HashMap<>();
        for (Element input : doc.select("input[type=hidden][name]")) {
            fields.put(input.attr("name"), input.attr("value"));
        }

        if (!fields.containsKey("__VIEWSTATE") || !fields.containsKey("__EVENTVALIDATION")) {
            throw new IOException("ASP.NET hidden fields not found on login page.");
        }

        byte[] captcha = getBytes(CAPTCHA_URL);

        return new LoginPageData(fields, captcha);
    }


    /** Returns true if login success, false if invalid credentials */
    public boolean login(String username, String password, String captcha, Map<String, String> hiddenFields) throws IOException {
        this.username = username;
        this.password = password;

        FormBody.Builder fb = new FormBody.Builder();
        for (Map.Entry<String, String> e : hiddenFields.entrySet()) {
            fb.add(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }

        fb.add("txtUserName", username);
        fb.add("txtPassword", password);
        fb.add("txtCaptcha", captcha);
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

    public static class SessionExpiredException extends IOException {
        public SessionExpiredException() {
            super("Session expired. Please log in again.");
        }
    }

    private void renewSession() throws IOException {
        throw new SessionExpiredException();
    }

    private void ensureSession() throws IOException {
        if (!isSessionValid()) renewSession();
    }

    // -------------------- PROFILE --------------------

    public StudentProfile fetchStudentProfile() throws IOException {
        ensureSession();
        String html = get(ATTENDANCE_URL);
        Document doc = Jsoup.parse(html);

        String studentName = textOrEmpty(doc.selectFirst("#MainContent_lblName"));
        String branch = textOrEmpty(doc.selectFirst("#MainContent_lblBranch"));

        return new StudentProfile(studentName, branch);
    }

    // -------------------- ATTENDANCE (SUMMARY GRID) --------------------

    public List<SubjectAttendanceItem> fetchAttendanceData() throws IOException {
        ensureSession();
        String html = get(ATTENDANCE_URL);
        Document doc = Jsoup.parse(html);

        List<SubjectAttendanceItem> attendanceList = new ArrayList<>();
        Elements rows = doc.select("#MainContent_GridView4 tr");

        for (int i = 1; i < rows.size(); i++) {
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

    public List<PeriodAttendanceItem> fetchSubjectFullAttendance(String subjectCode, String subjectName) throws IOException {
        ensureSession();

        String html = get(ATTENDANCE_URL);
        Document doc = Jsoup.parse(html);

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
        if (selectedYear == null) selectedYear = "2025";

        String monthVal = getFirstOrSelectedOptionValue(doc, "MainContent_DropDownList1");

        FormBody.Builder fb = new FormBody.Builder();

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

        fb.add("ctl00$MainContent$g1", "RadioButton3");

        fb.add("__EVENTTARGET", "");
        fb.add("__EVENTARGUMENT", "");
        fb.add("__LASTFOCUS", "");

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
        ensureSession();

        String html = get(ATTENDANCE_URL);
        Document doc = Jsoup.parse(html);

        String studentName = textOrEmpty(doc.selectFirst("#MainContent_lblName"));
        String branch      = textOrEmpty(doc.selectFirst("#MainContent_lblBranch"));

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

        Map<String, List<TimetableItem>> timetableByDay = new HashMap<>();
        Element tt = doc.getElementById("MainContent_GridTimetable");
        List<String> slots = new ArrayList<>();

        if (tt != null) {
            Elements rows = tt.select("tr");
            if (!rows.isEmpty()) {

                Elements ths = rows.get(0).select("th");
                for (int i = 1; i < ths.size(); i++) {
                    slots.add(ths.get(i).text().trim());
                }

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

                        String code = nameToCode.get(key(subjectName));
                        if (code == null || code.trim().isEmpty()) {
                            code = subjectName;
                        }

                        String status = computeStatusIfToday(day, time);
                        dayItems.add(new TimetableItem(code, subjectName, time, status));
                    }

                    timetableByDay.put(day, dayItems);
                }
            }
        }

        double overall = parseOverallAttendance(doc);

        // ✅ Fetch results and compute CGPA
        List<SemesterResult> results = fetchAllSemesterResultsRegular();
        double cgpa = computeCgpa(results);


        return new StudentDashboardData(studentName, branch, overall, cgpa, timetableByDay);
    }

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

            if (totalIdx == -1) totalIdx = 4;
            if (facultyIdx == -1) facultyIdx = 5;
            if (presentIdx == -1) presentIdx = 6;
            if (absentIdx == -1) absentIdx = 7;

            int sumPresent = 0;
            int sumDenom = 0;

            for (int r = 1; r < rows.size(); r++) {
                Elements tds = rows.get(r).select("td");
                if (tds.size() <= Math.max(Math.max(totalIdx, facultyIdx), presentIdx)) continue;

                int present = safeInt(tds.get(presentIdx).text());
                int faculty = safeInt(tds.get(facultyIdx).text());
                int absent  = (tds.size() > absentIdx) ? safeInt(tds.get(absentIdx).text()) : 0;
                int total   = safeInt(tds.get(totalIdx).text());

                int denom = faculty;
                if (denom <= 0) {
                    int pa = present + absent;
                    if (pa > 0) denom = pa;
                }
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

    // ==================== ✅ SEMESTER RESULTS (REAL) ====================

    public enum ResultType {
        REGULAR("RadioButton1", "ctl00$MainContent$RadioButton1"),
        ARREAR("RadioButton2", "ctl00$MainContent$RadioButton2"),
        REVALUATION("RadioButton3", "ctl00$MainContent$RadioButton3");

        public final String groupValue;     // ctl00$MainContent$group1
        public final String eventTarget;    // __EVENTTARGET

        ResultType(String groupValue, String eventTarget) {
            this.groupValue = groupValue;
            this.eventTarget = eventTarget;
        }
    }

    /** Returns YoPList values like "Nov.2025" */
    public List<String> fetchAvailableResultPeriods() throws IOException {
        ensureSession();
        String html = get(SEMESTER_MARK_URL);
        Document doc = Jsoup.parse(html);

        Element sel = doc.selectFirst("#MainContent_YoPList");
        if (sel == null) return new ArrayList<>();

        List<String> out = new ArrayList<>();
        for (Element opt : sel.select("option")) {
            String v = opt.attr("value");
            if (v == null) continue;
            v = v.trim();
            if (v.isEmpty() || "0".equals(v) || v.equalsIgnoreCase("Select YoR")) continue;
            out.add(v);
        }
        return out;
    }

    /** Fetches results for ONE period (e.g., Nov.2025) and ONE type (Regular/Arrear/Revaluation). */
    public List<SemesterResult> fetchSemesterResultsForPeriod(String periodValue, ResultType type) throws IOException {
        ensureSession();

        // Step-0: GET page
        PageState s0 = getSemesterMarkState();

        // Step-1: POST dropdown selection (YoPList postback)
        PageState s1 = postSemesterMark(
                s0,
                "ctl00$MainContent$YoPList",
                periodValue,
                null
        );

        // Step-2: POST radio selection (Regular/Arrear/Revaluation)
        PageState s2 = postSemesterMark(
                s1,
                type.eventTarget,
                periodValue,
                type.groupValue
        );

        // Parse final page for GridView1
        return parseSemesterResultsHtml(s2.html);
    }

    /** Fetches ALL periods and merges semesters (auto month/year change) */
    public List<SemesterResult> fetchAllSemesterResults(ResultType type) throws IOException {
        ensureSession();

        List<String> periods = fetchAvailableResultPeriods();
        if (periods == null || periods.isEmpty()) return new ArrayList<>();

        // Keep latest found per semester (if duplicates happen)
        Map<Integer, SemesterResult> semMap = new LinkedHashMap<>();

        for (String p : periods) {
            List<SemesterResult> one = fetchSemesterResultsForPeriod(p, type);
            for (SemesterResult sr : one) {
                int semesterNo = extractSemesterNumber(sr.semesterName);
                if (semesterNo <= 0) continue;

                // If already exists, keep the one with more subjects (safer)
                if (!semMap.containsKey(semesterNo)) {
                    semMap.put(semesterNo, sr);
                } else {
                    SemesterResult old = semMap.get(semesterNo);
                    int oldCount = (old.subjects == null) ? 0 : old.subjects.size();
                    int newCount = (sr.subjects == null) ? 0 : sr.subjects.size();
                    if (newCount > oldCount) semMap.put(semesterNo, sr);
                }
            }
        }

        List<SemesterResult> out = new ArrayList<>(semMap.values());
        Collections.sort(out, Comparator.comparingInt(o -> extractSemesterNumber(o.semesterName)));
        return out;
    }

    public List<SemesterResult> fetchAllSemesterResultsRegular() throws IOException {
        return fetchAllSemesterResults(ResultType.REGULAR);
    }

    // ---------- Internals for SemesterMark.aspx ----------

    private static class PageState {
        final Map<String, String> hidden;
        final String html;

        PageState(Map<String, String> hidden, String html) {
            this.hidden = hidden;
            this.html = html;
        }
    }

    private PageState getSemesterMarkState() throws IOException {
        String html = get(SEMESTER_MARK_URL);
        Document doc = Jsoup.parse(html);
        Map<String, String> hidden = extractAspNetHidden(doc);
        return new PageState(hidden, html);
    }

    private PageState postSemesterMark(PageState prev, String eventTarget, String periodValue, String group1Value) throws IOException {
        FormBody.Builder fb = new FormBody.Builder();

        // Must exist
        fb.add("__EVENTTARGET", eventTarget == null ? "" : eventTarget);
        fb.add("__EVENTARGUMENT", "");
        fb.add("__LASTFOCUS", "");

        // YoPList (month/year)
        if (periodValue != null) {
            fb.add("ctl00$MainContent$YoPList", periodValue);
        }

        // group1 (radio group)
        if (group1Value != null) {
            fb.add("ctl00$MainContent$group1", group1Value);
        }

        // Add hidden fields (VIEWSTATE / EVENTVALIDATION / GENERATOR etc) from previous page
        if (prev != null && prev.hidden != null) {
            for (Map.Entry<String, String> e : prev.hidden.entrySet()) {
                String k = e.getKey();
                if (k == null) continue;

                // Avoid duplicates for event fields (we already set above)
                if ("__EVENTTARGET".equals(k) || "__EVENTARGUMENT".equals(k) || "__LASTFOCUS".equals(k)) continue;

                fb.add(k, e.getValue() == null ? "" : e.getValue());
            }
        }

        Request post = new Request.Builder()
                .url(SEMESTER_MARK_URL)
                .post(fb.build())
                .header("Referer", SEMESTER_MARK_URL)
                .header("Origin", BASE)
                .header("User-Agent", "Mozilla/5.0")
                .build();

        String html;
        try (Response res = client.newCall(post).execute()) {
            if (!res.isSuccessful() || res.body() == null) {
                throw new IOException("SemesterMark POST failed: " + res.code());
            }
            html = res.body().string();
        }

        Document doc = Jsoup.parse(html);
        Map<String, String> hidden = extractAspNetHidden(doc);
        return new PageState(hidden, html);
    }

    private static Map<String, String> extractAspNetHidden(Document doc) {
        Map<String, String> map = new HashMap<>();
        if (doc == null) return map;
        for (Element input : doc.select("input[type=hidden][name]")) {
            String name = input.attr("name");
            String value = input.attr("value");
            if (name != null && !name.trim().isEmpty()) {
                map.put(name, value == null ? "" : value);
            }
        }
        return map;
    }

    private List<SemesterResult> parseSemesterResultsHtml(String html) {
        List<SemesterResult> out = new ArrayList<>();
        if (html == null || html.trim().isEmpty()) return out;

        Document doc = Jsoup.parse(html);

        Element table = doc.getElementById("MainContent_GridView1");
        if (table == null) return out;

        Elements rows = table.select("tr");
        if (rows.size() <= 1) return out;

        // semesterNo -> subjects
        Map<Integer, List<SubjectGrade>> semSubjects = new HashMap<>();

        for (int i = 1; i < rows.size(); i++) {
            Elements tds = rows.get(i).select("td");
            if (tds.size() < 7) continue;

            int semesterNo = safeInt(tds.get(2).text()); // "1", "2", ...
            String courseName = tds.get(4).text().trim();
            String grade = tds.get(6).text().trim();

            if (semesterNo <= 0) continue;

            List<SubjectGrade> list = semSubjects.get(semesterNo);
            if (list == null) list = new ArrayList<>();

            list.add(new SubjectGrade(courseName, grade));
            semSubjects.put(semesterNo, list);
        }

        List<Integer> semNos = new ArrayList<>(semSubjects.keySet());
        Collections.sort(semNos);

        for (Integer semNo : semNos) {
            List<SubjectGrade> subjects = semSubjects.get(semNo);
            double tgpa = computeTgpaEqualWeight(subjects);
            out.add(new SemesterResult("Semester " + semNo, tgpa, subjects));
        }

        return out;
    }

    /** TGPA approximation: equal weight per subject (credits not available on SemesterMark.aspx). */
    private double computeTgpaEqualWeight(List<SubjectGrade> subjects) {
        if (subjects == null || subjects.isEmpty()) return 0.0;

        double sum = 0;
        int count = 0;

        for (SubjectGrade sg : subjects) {
            if (sg == null) continue;
            String g = (sg.grade == null) ? "" : sg.grade.trim().toUpperCase(Locale.US);
            if (g.isEmpty()) continue;

            double p = gradeToPoint(g);
            // include fails/RA as 0 in average
            sum += p;
            count++;
        }

        if (count == 0) return 0.0;

        double v = sum / count;
        // round 2 decimals
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * CGPA = Sum of (TGPA * number of subjects in sem) / Total number of subjects
     * This is an approximation as we don't have credits.
     */
    private double computeCgpa(List<SemesterResult> semesterResults) {
        if (semesterResults == null || semesterResults.isEmpty()) return 0.0;

        double totalPoints = 0;
        int totalSubjects = 0;

        for (SemesterResult sr : semesterResults) {
            if (sr == null || sr.subjects == null || sr.subjects.isEmpty()) continue;

            int numSubjects = sr.subjects.size();
            totalPoints += sr.tgpa * numSubjects;
            totalSubjects += numSubjects;
        }

        if (totalSubjects == 0) return 0.0;

        double v = totalPoints / totalSubjects;
        // round 2 decimals
        return Math.round(v * 100.0) / 100.0;
    }

    /** Grade → Point mapping (best-effort; AMS page doesn’t provide credits/points). */
    private double gradeToPoint(String g) {
        if (g == null) return 0;

        g = g.trim().toUpperCase(Locale.US);

        // common grades seen
        switch (g) {
            case "O":  return 10.0;
            case "S":  return 9.0;
            case "A+": return 9.0;
            case "A":  return 8.0;
            case "B+": return 7.0;
            case "B":  return 6.0;
            case "C":  return 5.0;
            case "D":  return 4.0;

            // withheld / fail-like
            case "RA":
            case "F":
            case "AB":
            case "NE":
            case "NC":
            case "ND":
            case "WH1":
            case "WH2":
            case "WH3":
            case "WH4":
                return 0.0;
        }

        // if it contains WH*
        if (g.startsWith("WH")) return 0.0;

        return 0.0;
    }

    private int extractSemesterNumber(String semesterName) {
        if (semesterName == null) return 0;
        Matcher m = Pattern.compile("(\\d+)").matcher(semesterName);
        if (m.find()) return safeInt(m.group(1));
        return 0;
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

    private static String getFirstOrSelectedOptionValue(Document doc, String selectId) {
        Element sel = doc.getElementById(selectId);
        if (sel == null) return null;

        Element selected = sel.selectFirst("option[selected]");
        if (selected != null) return selected.attr("value");

        Element first = sel.selectFirst("option");
        if (first != null) return first.attr("value");

        return null;
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

    private byte[] getBytes(String url) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Mozilla/5.0")
                .build();

        try (Response res = client.newCall(req).execute()) {
            if (!res.isSuccessful() || res.body() == null) {
                throw new IOException("HTTP " + res.code() + " for " + url);
            }
            return res.body().bytes();
        }
    }

    private static String textOrEmpty(Element e) {
        return e == null ? "" : e.text().trim();
    }

    public static String getTodayWeekdayNameStatic() {
        return new SimpleDateFormat("EEEE", Locale.US).format(new Date());
    }
}
