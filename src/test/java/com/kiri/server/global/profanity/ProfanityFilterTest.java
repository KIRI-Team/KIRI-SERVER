package com.kiri.server.global.profanity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProfanityFilterTest {

    private final ProfanityFilter profanityFilter = new ProfanityFilter();

    @ParameterizedTest
    @ValueSource(strings = {"씨발", "행복한시발", "개새끼네", "병신가족", "병신년", "섹스왕", "맘충", "쉬벌", "좃같네", "뻑큐", "샤갈", "틀니딱딱", "전라디언", "개독"})
    @DisplayName("금칙어가 포함되면 차단한다.")
    void containsBannedWord_profanity(String text) {
        assertThat(profanityFilter.containsBannedWord(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FUCK", "FuckYou", "  씨발  "})
    @DisplayName("영문 대소문자와 앞뒤 공백은 무시하고 검사한다.")
    void containsBannedWord_ignoresCaseAndOuterSpaces(String text) {
        assertThat(profanityFilter.containsBannedWord(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"시 발 가족", "시1발", "시8", "씨  발"})
    @DisplayName("띄어쓰기나 숫자를 끼워 넣은 변형도 차단한다.")
    void containsBannedWord_obviousVariants(String text) {
        assertThat(profanityFilter.containsBannedWord(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"시발점", "다시 발견한 우리", "다시 벌어보자", "가족같은 우리", "새끼고양이", "자위대", "한강간다", "행복한 우리집", "민수네 가족", "bad123"})
    @DisplayName("정상 단어는 차단하지 않는다.")
    void containsBannedWord_allowsNormalWords(String text) {
        assertThat(profanityFilter.containsBannedWord(text)).isFalse();
    }

    @Test
    @DisplayName("화이트리스트 단어 밖에 있는 금칙어는 차단한다.")
    void containsBannedWord_blocksOutsideAllowedWord() {
        assertThat(profanityFilter.containsBannedWord("시발점시발")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"보지", "자지", "우리 보지", "SEX"})
    @DisplayName("단어 단위 금칙어는 단어 전체가 일치하면 차단한다.")
    void containsBannedWord_wholeWord(String text) {
        assertThat(profanityFilter.containsBannedWord(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"보지마", "혼자지만", "essex", "메갈로돈", "매일베이킹", "무지개독서", "japan"})
    @DisplayName("단어 단위 금칙어는 다른 단어의 일부일 때 차단하지 않는다.")
    void containsBannedWord_wholeWordNotPartial(String text) {
        assertThat(profanityFilter.containsBannedWord(text)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"운영자", "끼리운영자", "끼리 공식", "Admin", "KIRI", "공식", "GM"})
    @DisplayName("운영자 사칭 표현은 차단한다.")
    void containsBannedWord_reserved(String text) {
        assertThat(profanityFilter.containsBannedWord(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"배드민턴", "badminton", "끼리", "우리끼리", "가족 끼리", "공식적인가족", "운영의신"})
    @DisplayName("예약어가 정상 단어의 일부이면 차단하지 않는다.")
    void containsBannedWord_reservedNotPartial(String text) {
        assertThat(profanityFilter.containsBannedWord(text)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("null이나 빈 문자열은 통과한다.")
    void containsBannedWord_blank(String text) {
        assertThat(profanityFilter.containsBannedWord(text)).isFalse();
    }
}
