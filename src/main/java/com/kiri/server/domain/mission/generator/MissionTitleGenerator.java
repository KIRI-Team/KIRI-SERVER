package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.mission.enums.Category;

public class MissionTitleGenerator {

    private static final int MAX_TITLE_LENGTH = 100;
    private static final String EXPERIENCE_WORD = "체험";

    public static String generate(Category category, String originName) {
        String name = originName.replaceAll("\\s+", " ").trim();
        return switch (category) {
            case POPUP, FOOD_CAFE -> fit(name, "", " 방문하기");
            case EXHIBITION, PERFORMANCE -> fit(name, "", " 관람하기");
            case OUTDOOR -> fit(name, "가족과 ", " 나들이 가기");
            case EXPERIENCE -> name.endsWith(EXPERIENCE_WORD)
                    ? fit(name, "가족과 ", "하기")
                    : fit(name, "", " 방문하기");
        };
    }

    private static String fit(String name, String prefix, String suffix) {
        int room = MAX_TITLE_LENGTH - prefix.length() - suffix.length();
        String fitted = name.length() <= room ? name : name.substring(0, room).trim();
        return prefix + fitted + suffix;
    }
}
