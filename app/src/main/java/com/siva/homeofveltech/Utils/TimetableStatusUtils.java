package com.siva.homeofveltech.Utils;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimetableStatusUtils {

    private TimetableStatusUtils() {
    }

    public static String computeStatusForDay(String dayName, String timeRange) {
        String normalizedDay = normalizeDayName(dayName);
        String today = normalizeDayName(getTodayWeekdayName());

        if (!normalizedDay.equalsIgnoreCase(today)) return "Upcoming";

        int[] range = parseSlotMinutes(timeRange);
        if (range == null) return "Upcoming";

        Calendar cal = Calendar.getInstance();
        int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        int start = range[0];
        int end = range[1];

        if (now < start) return "Upcoming";
        if (now <= end) return "On Going";
        return "Completed";
    }

    private static int[] parseSlotMinutes(String slot) {
        if (slot == null) return null;
        String s = slot.trim().toUpperCase(Locale.US).replaceAll("\\s+", "");
        s = s.replace('\u2013', '-') // en dash
                .replace('\u2014', '-') // em dash
                .replace('\u2212', '-'); // minus sign

        // Example: 2.45-3.35PM or 2:45-3:35PM
        Pattern pSingleMeridiem = Pattern.compile(
                "([0-9]{1,2})(?:\\.|:)([0-9]{1,2})-([0-9]{1,2})(?:\\.|:)([0-9]{1,2})(AM|PM)"
        );
        Matcher m1 = pSingleMeridiem.matcher(s);
        if (m1.find()) {
            String endAmPm = m1.group(5);
            int end = hmToMinutes(m1.group(3), m1.group(4), endAmPm);
            int start = inferStartMinutes(m1.group(1), m1.group(2), end, endAmPm);
            if (start < 0) return null;
            return new int[]{start, end};
        }

        // Example: 2.45PM-3.35PM
        Pattern pDualMeridiem = Pattern.compile(
                "([0-9]{1,2})(?:\\.|:)([0-9]{1,2})(AM|PM)-([0-9]{1,2})(?:\\.|:)([0-9]{1,2})(AM|PM)"
        );
        Matcher m2 = pDualMeridiem.matcher(s);
        if (m2.find()) {
            int start = hmToMinutes(m2.group(1), m2.group(2), m2.group(3));
            int end = hmToMinutes(m2.group(4), m2.group(5), m2.group(6));
            if (end < start) end += 12 * 60;
            return new int[]{start, end};
        }

        return null;
    }

    private static int hmToMinutes(String h, String m, String ampm) {
        int hour = safeInt(h);
        int min = safeInt(m);
        if (min > 59) min = min / 10; // handle odd one-digit minute style safely

        if ("AM".equals(ampm)) {
            if (hour == 12) hour = 0;
        } else {
            if (hour != 12) hour += 12;
        }
        return hour * 60 + min;
    }

    private static int inferStartMinutes(String h, String m, int endMinutes, String endAmPm) {
        // If end is AM, start is almost always AM for these timetable ranges.
        if ("AM".equals(endAmPm)) {
            int start = hmToMinutes(h, m, "AM");
            if (start <= endMinutes) return start;
            int wrapped = start - (12 * 60);
            return Math.max(wrapped, 0);
        }

        // End is PM: choose AM/PM start that gives a realistic class duration.
        int startAm = hmToMinutes(h, m, "AM");
        int startPm = hmToMinutes(h, m, "PM");

        int durAm = endMinutes - startAm;
        int durPm = endMinutes - startPm;

        // Prefer non-wrapped realistic durations first.
        if (isLikelyClassDuration(durAm) && startAm <= endMinutes) return startAm;
        if (isLikelyClassDuration(durPm) && startPm <= endMinutes) return startPm;

        // Then allow 12-hour wrapped durations.
        int wrapAm = durAm + 12 * 60;
        int wrapPm = durPm + 12 * 60;
        if (isLikelyClassDuration(wrapAm)) return startAm;
        if (isLikelyClassDuration(wrapPm)) return startPm;

        return -1;
    }

    private static boolean isLikelyClassDuration(int minutes) {
        return minutes > 0 && minutes <= 180;
    }

    private static int safeInt(String s) {
        try {
            return Integer.parseInt(s == null ? "0" : s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String getTodayWeekdayName() {
        Calendar c = Calendar.getInstance();
        switch (c.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY:
                return "Monday";
            case Calendar.TUESDAY:
                return "Tuesday";
            case Calendar.WEDNESDAY:
                return "Wednesday";
            case Calendar.THURSDAY:
                return "Thursday";
            case Calendar.FRIDAY:
                return "Friday";
            case Calendar.SATURDAY:
                return "Saturday";
            case Calendar.SUNDAY:
            default:
                return "Sunday";
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
}
