package com.kiri.server.global.ai.factory;

import com.kiri.server.domain.question.enums.TasteType;

public final class PromptFactory {

    private PromptFactory() {}

    // 나중에 수정 필요 (일단 초안만 작성)
    public static String preferenceClassification(TasteType tasteType, String question, String answer) {
        return switch (tasteType) {
            case CATEGORY -> category(question, answer);
            case INDOOR_OUTDOOR -> indoorOutdoor(question, answer);
            case DURATION -> duration(question, answer);
            case PRICE -> price(question, answer);
            case STEPS -> steps(question, answer);
        };
    }


    private static String category(String question, String answer) {
        return """
                사용자의 답변에서 선호하는 활동 카테고리를 분류해주세요.

                [질문]
                %s

                [사용자 답변]
                %s

                [분류 가능한 카테고리]
                - EXHIBITION: 전시회, 미술관, 박물관 등
                - POPUP: 팝업스토어, 팝업 전시 등
                - PERFORMANCE: 공연, 콘서트, 연극, 뮤지컬 등
                - FOOD_CAFE: 음식점, 카페, 맛집 탐방 등
                - OUTDOOR: 산책, 공원, 등산 등 야외 활동
                - EXPERIENCE: 공방, 체험 활동, 만들기 등

                [분류 규칙]
                - 질문의 내용과 사용자의 답변을 함께 고려해주세요.
                - 사용자가 선호한다고 명확하게 표현한 활동만 선택해주세요.
                - 답변에 단순히 언급된 활동은 선호한다고 판단하지 마세요.
                - 최대 3개까지 선택할 수 있습니다.
                - 선호를 판단할 근거가 부족하면 빈 배열을 반환해주세요.
                - 반드시 위에 정의된 카테고리만 사용해주세요.

                [응답 형식]
                반드시 아래 JSON 형식으로만 응답해주세요.
                JSON 외의 설명이나 Markdown은 포함하지 마세요.

                {
                  "categories": ["EXHIBITION", "POPUP"]
                }
                """.formatted(question, answer);
    }

    private static String indoorOutdoor(String question, String answer) {
        return """
                사용자의 답변에서 선호하는 활동 공간을 분류해주세요.

                [질문]
                %s

                [사용자 답변]
                %s

                [분류 가능한 값]
                - INDOOR: 실내 활동 선호
                - OUTDOOR: 야외 활동 선호

                [분류 규칙]
                - 질문의 내용과 사용자의 답변을 함께 고려해주세요.
                - 사용자의 명확한 공간 선호를 기준으로 판단해주세요.
                - 판단할 근거가 부족하면 null을 반환해주세요.
                - 반드시 INDOOR 또는 OUTDOOR 중 하나만 반환해주세요.

                [응답 형식]
                반드시 아래 JSON 형식으로만 응답해주세요.
                JSON 외의 설명이나 Markdown은 포함하지 마세요.

                {
                  "indoorOutdoor": "INDOOR"
                }
                """.formatted(question, answer);
    }

    private static String duration(String question, String answer) {
        return """
                사용자의 답변에서 선호하는 활동 시간을 분류해주세요.

                [질문]
                %s

                [사용자 답변]
                %s

                [분류 가능한 값]
                - WITHIN_1H: 1시간 이내
                - H1_TO_3: 1시간 초과 3시간 이하
                - HALF_DAY_PLUS: 반나절 이상

                [분류 규칙]
                - 질문의 내용과 사용자의 답변을 함께 고려해주세요.
                - 사용자가 선호하는 활동 시간을 기준으로 판단해주세요.
                - 답변에 시간에 대한 충분한 근거가 없다면 null을 반환해주세요.
                - 반드시 위에 정의된 값 중 하나만 사용해주세요.

                [응답 형식]
                반드시 아래 JSON 형식으로만 응답해주세요.
                JSON 외의 설명이나 Markdown은 포함하지 마세요.

                {
                  "durationLevel": "H1_TO_3"
                }
                """.formatted(question, answer);
    }

    private static String price(String question, String answer) {
        return """
                사용자의 답변에서 선호하는 활동 가격 수준을 분류해주세요.

                [질문]
                %s

                [사용자 답변]
                %s

                [분류 가능한 값]
                - FREE: 무료 활동 선호
                - LOW: 저렴한 비용의 활동 선호

                [분류 규칙]
                - 질문의 내용과 사용자의 답변을 함께 고려해주세요.
                - 무료 활동을 선호한다고 명확하게 표현하면 FREE로 분류해주세요.
                - 저렴한 비용의 활동을 선호한다고 표현하면 LOW로 분류해주세요.
                - 가격 선호를 판단할 충분한 근거가 없다면 null을 반환해주세요.
                - 반드시 FREE 또는 LOW만 사용해주세요.

                [응답 형식]
                반드시 아래 JSON 형식으로만 응답해주세요.
                JSON 외의 설명이나 Markdown은 포함하지 마세요.

                {
                  "priceLevel": "FREE"
                }
                """.formatted(question, answer);
    }

    private static String steps(String question, String answer) {
        return """
                사용자의 답변에서 선호하는 활동 강도를 분류해주세요.

                [질문]
                %s

                [사용자 답변]
                %s

                [분류 가능한 값]
                - LIGHT: 가벼운 활동
                - MODERATE: 적당한 활동
                - ACTIVE: 활동량이 많은 활동

                [분류 규칙]
                - 질문의 내용과 사용자의 답변을 함께 고려해주세요.
                - 사용자가 선호하는 활동량과 답변에 나타난 활동의 성격을 종합하여 판단해주세요.
                - 반드시 LIGHT, MODERATE, ACTIVE 중 하나를 선택해주세요.
                - 판단하기 어려운 경우에도 가장 적절한 하나를 선택해주세요.

                [응답 형식]
                반드시 아래 JSON 형식으로만 응답해주세요.
                JSON 외의 설명이나 Markdown은 포함하지 마세요.

                {
                  "activityLevel": "MODERATE"
                }
                """.formatted(question, answer);
    }
}
