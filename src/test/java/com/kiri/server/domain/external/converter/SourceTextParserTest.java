package com.kiri.server.domain.external.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class SourceTextParserTest {

    @Test
    @DisplayName("HTML 태그와 엔티티를 지우고 줄바꿈은 유지한다.")
    void stripHtml() {
        String text = SourceTextParser.stripHtml("한강을 걷는 축제<br>&nbsp;가족 참여 환영&amp;사전등록 <b>불필요</b>");

        assertThat(text).isEqualTo("한강을 걷는 축제\n가족 참여 환영&사전등록 불필요");
    }

    @Test
    @DisplayName("태그만 있거나 비어 있으면 null을 반환한다.")
    void stripHtml_empty() {
        assertThat(SourceTextParser.stripHtml("<br><br>&nbsp;")).isNull();
        assertThat(SourceTextParser.stripHtml(null)).isNull();
    }

    @Test
    @DisplayName("yyyyMMdd 날짜를 읽고 형식이 다르면 null을 반환한다.")
    void parseCompactDate() {
        assertThat(SourceTextParser.parseCompactDate("20261003")).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(SourceTextParser.parseCompactDate("2026-10-03")).isNull();
    }

    @Test
    @DisplayName("금액이 여러 개면 가장 낮은 금액을 쓴다.")
    void parseFee_lowestAmount() {
        assertThat(SourceTextParser.parseFee("어른 3,000원, 어린이 1,500원")).isEqualTo(1500);
    }

    @Test
    @DisplayName("금액 없이 무료만 있으면 0, 판단할 수 없으면 null이다.")
    void parseFee_freeOrUnknown() {
        assertThat(SourceTextParser.parseFee("무료")).isZero();
        assertThat(SourceTextParser.parseFee("유료(현장 문의)")).isNull();
        assertThat(SourceTextParser.parseFee(null)).isNull();
    }

    @Test
    @DisplayName("시간대가 하나면 운영시간을 읽고, 24:00은 23:59로 읽는다.")
    void parseTimeRange() {
        assertThat(SourceTextParser.parseTimeRange("10시~17시"))
                .isEqualTo(new SourceTextParser.TimeRange(LocalTime.of(10, 0), LocalTime.of(17, 0)));
        assertThat(SourceTextParser.parseTimeRange("09:00 - 24:00"))
                .isEqualTo(new SourceTextParser.TimeRange(LocalTime.of(9, 0), LocalTime.of(23, 59)));
    }

    @Test
    @DisplayName("계절별로 시간대가 여러 개면 추측하지 않고 null을 반환한다.")
    void parseTimeRange_multiple() {
        assertThat(SourceTextParser.parseTimeRange("09:00~18:00 (3~10월) / 09:00~17:00 (11~2월)")).isNull();
        assertThat(SourceTextParser.parseTimeRange("상시 개방")).isNull();
    }

    @Test
    @DisplayName("소요시간을 분 단위 범위로 읽는다.")
    void parseDurationMinutes() {
        assertThat(SourceTextParser.parseDurationMinutes("1~2시간"))
                .isEqualTo(new SourceTextParser.MinuteRange(60, 120));
        assertThat(SourceTextParser.parseDurationMinutes("관람 1시간 30분 소요"))
                .isEqualTo(new SourceTextParser.MinuteRange(90, 90));
        assertThat(SourceTextParser.parseDurationMinutes("약 90분"))
                .isEqualTo(new SourceTextParser.MinuteRange(90, 90));
        assertThat(SourceTextParser.parseDurationMinutes("상시")).isNull();
    }

    @Test
    @DisplayName("프로그램 안내를 짧은 항목으로 나누고 긴 문장은 버린다.")
    void splitItems() {
        String text = "① 도자기 만들기 ② 떡메치기\n- 전통 놀이 체험, 가족 퀴즈 / "
                + "이 항목은 너무 길어서 필수 활동 제목으로 쓰기에는 적합하지 않습니다";

        assertThat(SourceTextParser.splitItems(text))
                .containsExactly("도자기 만들기", "떡메치기", "전통 놀이 체험", "가족 퀴즈");
    }
    @Test
    @DisplayName("기간·장소 같은 안내 줄과 날짜 조각, 소제목은 활동으로 쓰지 않는다.")
    void splitItems_skipsLabelsDatesAndSectionTitles() {
        String text = "기간 : 2026년 10월 18일, 25일, 11월 8일\n장소 : 서울숲\n메인 프로그램\n요가, 러닝 클래스\n공식행사, 폐막식";

        assertThat(SourceTextParser.splitItems(text)).containsExactly("요가", "러닝 클래스", "폐막식");
    }

    @Test
    @DisplayName("괄호 안의 구분 기호에서는 나누지 않고, 닫히지 않은 괄호는 떼어낸다.")
    void splitItems_keepsBrackets() {
        assertThat(SourceTextParser.splitItems("무대공연, 초청공연(원슈타인, 이영지), 예술 등(燈)"))
                .containsExactly("무대공연", "초청공연(원슈타인, 이영지)", "예술 등(燈)");
        assertThat(SourceTextParser.splitItems("무대공연, 초청공연(원슈타인, 이영지"))
                .containsExactly("무대공연", "초청공연");
    }

    @Test
    @DisplayName("설명이 붙은 항목은 앞부분만 쓰고, 끝의 '등'·'외'는 뗀다.")
    void splitItems_trimsDetailsAndEtc() {
        assertThat(SourceTextParser.splitItems("서초-한강 아트투어(ART TOUR) : 고터에서 출발"))
                .containsExactly("서초-한강 아트투어(ART TOUR)");
        assertThat(SourceTextParser.splitItems("순대국, 술국, 수육 등\n돼지구이 외"))
                .containsExactly("순대국", "술국", "수육", "돼지구이");
    }
}
