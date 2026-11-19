package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.mission.enums.Category;

public class MissionTitleGenerator {

    private static final String EXPERIENCE_WORD = "체험";

    public static String generate(Category category, String originName) {
        String name = originName.replaceAll("\\s+", " ").trim();
        return switch (category) {
            case POPUP, FOOD_CAFE -> name + " 방문하기";
            case EXHIBITION, PERFORMANCE -> name + " 관람하기";
            case OUTDOOR -> "가족과 " + name + " 나들이 가기";
            case EXPERIENCE -> name.endsWith(EXPERIENCE_WORD) ? "가족과 " + name + "하기" : name + " 방문하기";
        };
    }
}
