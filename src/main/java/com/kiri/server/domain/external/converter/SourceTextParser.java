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
    private static final Pattern FEE_LABEL_BOUNDARY = Pattern.compile("[\\n,/·ㆍ()（）\\[\\]]");
    private static final Pattern ADULT_FEE = Pattern.compile("성인|어른|대인|일반");
    private static final Pattern GROUP_FEE = Pattern.compile("(?<!\\d)(?:[2-9]|\\d{2,})\\s*인|가족|단체|패키지");

    private static final Pattern TIME_RANGE = Pattern.compile(
            "(\\d{1,2})\\s*(?::|시)\\s*(\\d{2})?\\s*분?\\s*[~\\-–]\\s*(\\d{1,2})\\s*(?::|시)\\s*(\\d{2})?");

    private static final Pattern HOUR_RANGE = Pattern.compile("(\\d+)\\s*[~\\-–]\\s*(\\d+)\\s*시간");
    private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*시간");
    private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*분");
    private static final int MAX_DURATION_MINUTES = 12 * 60;

    private static final String ITEM_SEPARATORS = ",/·ㆍ•▶▷■□◆◇○●";
    private static final String OPEN_BRACKETS = "([（【";
    private static final String CLOSE_BRACKETS = ")]）】";
    private static final Pattern ITEM_PREFIX = Pattern.compile("^(?:\\d+[.)]|[①-⑳]|[-*※])\\s*");
    private static final Pattern LABEL_LINE = Pattern.compile(
            "^(?:기간|일시|일정|날짜|시간|장소|위치|대상|요금|참가비|비용|문의|주최|주관|후원|신청|접수|운영|정원|인원)\\s*[:：]");
    private static final Pattern AFTER_COLON = Pattern.compile("\\s*[:：].*$");
    private static final Pattern DATE_ONLY = Pattern.compile("^[\\d\\s.~\\-–년월일시분()（）화수목금토요:]+$");
    private static final Pattern SECTION_TITLE = Pattern.compile(
            "^(?:메인|주요|연계|부대|공식|기타|특별|상설|세부|본)?\\s*(?:프로그램|행사|이벤트)$");
    private static final Pattern TRAILING_ETC = Pattern.compile("\\s+(?:등|외)$");
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

    // 1인 기준 요금(원): 성인·일반 요금 우선, 단체·가족 요금 제외, 1인 가격을 알 수 없으면 null
    public static Integer parseFee(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Integer adultLowest = null;
        Integer perPersonLowest = null;
        boolean hasAmount = false;
        int labelStart = 0;
        Matcher matcher = AMOUNT.matcher(text);
        while (matcher.find()) {
            hasAmount = true;
            int amount = Integer.parseInt(matcher.group(1).replace(",", ""));
            String label = feeLabel(text.substring(labelStart, matcher.start()));
            labelStart = matcher.end();
            if (GROUP_FEE.matcher(label).find()) {
                continue;
            }
            if (ADULT_FEE.matcher(label).find()) {
                adultLowest = min(adultLowest, amount);
            }
            perPersonLowest = min(perPersonLowest, amount);
        }
        if (adultLowest != null) {
            return adultLowest;
        }
        if (perPersonLowest != null) {
            return perPersonLowest;
        }
        if (hasAmount) {
            return null;
        }
        return text.contains(FREE) ? 0 : null;
    }

    // 직전 금액 뒤 ~ 이번 금액 앞 구간에서 마지막 구분자(줄바꿈·쉼표·괄호 등) 이후만 이번 금액의 대상 표기
    private static String feeLabel(String between) {
        Matcher boundary = FEE_LABEL_BOUNDARY.matcher(between);
        int labelStart = 0;
        while (boundary.find()) {
            labelStart = boundary.end();
        }
        return between.substring(labelStart);
    }

    private static Integer min(Integer current, int candidate) {
        return current == null || candidate < current ? candidate : current;
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
        for (String line : text.split("\\n")) {
            if (LABEL_LINE.matcher(line.trim()).find()) {
                continue;
            }
            for (String part : splitOutsideBrackets(line)) {
                String item = cleanItem(part);
                if (isActivity(item)) {
                    items.add(item);
                }
            }
        }
        return List.copyOf(items);
    }

    private static List<String> splitOutsideBrackets(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (char c : line.toCharArray()) {
            if (OPEN_BRACKETS.indexOf(c) >= 0) {
                depth++;
            } else if (CLOSE_BRACKETS.indexOf(c) >= 0 && depth > 0) {
                depth--;
            }
            boolean circledNumber = c >= '①' && c <= '⑳';
            if (depth == 0 && (ITEM_SEPARATORS.indexOf(c) >= 0 || circledNumber)) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static String cleanItem(String part) {
        String item = ITEM_PREFIX.matcher(part.trim()).replaceFirst("").trim();
        item = AFTER_COLON.matcher(item).replaceFirst("");
        item = TRAILING_ETC.matcher(item).replaceFirst("");
        return dropUnclosedBracket(item).trim();
    }

    private static String dropUnclosedBracket(String item) {
        int open = 0;
        int lastOpen = -1;
        for (int i = 0; i < item.length(); i++) {
            char c = item.charAt(i);
            if (OPEN_BRACKETS.indexOf(c) >= 0) {
                open++;
                lastOpen = i;
            } else if (CLOSE_BRACKETS.indexOf(c) >= 0 && open > 0) {
                open--;
            }
        }
        return open > 0 ? item.substring(0, lastOpen) : item;
    }

    private static boolean isActivity(String item) {
        return item.length() >= MIN_ITEM_LENGTH
                && item.length() <= MAX_ITEM_LENGTH
                && !DATE_ONLY.matcher(item).matches()
                && !SECTION_TITLE.matcher(item).matches();
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
