package com.kiri.server.domain.external.converter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceTextParser {

    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Pattern LINE_BREAK_TAG = Pattern.compile("(?i)<br\\s*/?>|</p>|</li>");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0]+");

    private static final Pattern AMOUNT = Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원");
    private static final String FREE = "무료";

    private static final Pattern TIME_RANGE = Pattern.compile(
            "(\\d{1,2})\\s*(?::|시)\\s*(\\d{2})?\\s*분?\\s*[~\\-–]\\s*(\\d{1,2})\\s*(?::|시)\\s*(\\d{2})?");

    private static final Pattern HOUR_RANGE = Pattern.compile("(\\d+)\\s*[~\\-–]\\s*(\\d+)\\s*시간");
    private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*시간");
    private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*분");
    private static final int MAX_DURATION_MINUTES = 12 * 60;

    private static final Pattern ITEM_SEPARATOR = Pattern.compile("\\n|,|/|·|ㆍ|•|▶|▷|■|□|◆|◇|○|●|[①-⑳]");
    private static final Pattern ITEM_PREFIX = Pattern.compile("^(?:\\d+[.)]|[①-⑳]|[-*※])\\s*");
    private static final int MIN_ITEM_LENGTH = 2;
    private static final int MAX_ITEM_LENGTH = 30;

    public static String stripHtml(String text) {
        if (text == null) {
            return null;
        }
        String withBreaks = LINE_BREAK_TAG.matcher(text).replaceAll("\n");
        String noTags = TAG.matcher(withBreaks).replaceAll("");
        String decoded = noTags
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");

        List<String> lines = new ArrayList<>();
        for (String line : decoded.split("\\r?\\n")) {
            String trimmed = SPACES.matcher(line).replaceAll(" ").trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    public static LocalDate parseCompactDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), COMPACT_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static Integer parseFee(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = AMOUNT.matcher(text);
        Integer lowest = null;
        while (matcher.find()) {
            int amount = Integer.parseInt(matcher.group(1).replace(",", ""));
            if (lowest == null || amount < lowest) {
                lowest = amount;
            }
        }
        if (lowest != null) {
            return lowest;
        }
        return text.contains(FREE) ? 0 : null;
    }

    public static TimeRange parseTimeRange(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = TIME_RANGE.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        LocalTime open = toTime(matcher.group(1), matcher.group(2));
        LocalTime close = toTime(matcher.group(3), matcher.group(4));
        if (matcher.find() || open == null || close == null) {
            return null;
        }
        return new TimeRange(open, close);
    }

    public static MinuteRange parseDurationMinutes(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher range = HOUR_RANGE.matcher(text);
        if (range.find()) {
            return validRange(Integer.parseInt(range.group(1)) * 60, Integer.parseInt(range.group(2)) * 60);
        }
        int total = 0;
        Matcher hours = HOURS.matcher(text);
        if (hours.find()) {
            total += Integer.parseInt(hours.group(1)) * 60;
        }
        Matcher minutes = MINUTES.matcher(text);
        if (minutes.find()) {
            total += Integer.parseInt(minutes.group(1));
        }
        return total == 0 ? null : validRange(total, total);
    }

    public static List<String> splitItems(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> items = new LinkedHashSet<>();
        for (String part : ITEM_SEPARATOR.split(text)) {
            String item = ITEM_PREFIX.matcher(part.trim()).replaceFirst("").trim();
            if (item.length() >= MIN_ITEM_LENGTH && item.length() <= MAX_ITEM_LENGTH) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    private static LocalTime toTime(String hourText, String minuteText) {
        int hour = Integer.parseInt(hourText);
        int minute = minuteText == null ? 0 : Integer.parseInt(minuteText);
        if (hour == 24 && minute == 0) {
            return LocalTime.of(23, 59);
        }
        if (hour > 23 || minute > 59) {
            return null;
        }
        return LocalTime.of(hour, minute);
    }

    private static MinuteRange validRange(int min, int max) {
        if (min <= 0 || max < min || max > MAX_DURATION_MINUTES) {
            return null;
        }
        return new MinuteRange(min, max);
    }

    public record TimeRange(LocalTime open, LocalTime close) {
    }

    public record MinuteRange(int min, int max) {
    }
}
