package com.kiri.server.global.profanity;

import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// 닉네임·가족명 금칙어 검사
// 매칭 엔진(aho-corasick) 의존은 이 클래스 안에만 둔다.
@Component
public class ProfanityFilter {

    private static final String BLOCKLIST_PATH = "profanity/blocklist.txt";
    private static final String RESERVED_PATH = "profanity/reserved.txt";
    private static final String ALLOWLIST_PATH = "profanity/allowlist.txt";

    private static final String COMMENT_PREFIX = "#";
    private static final String WHOLE_WORD_PREFIX = "=";

    // 단어 경계: 글자·숫자가 아닌 모든 문자(공백, 문장부호, 기호)
    private static final Pattern WORD_BOUNDARY = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final Dictionary profanity;
    private final Dictionary reserved;
    private final Trie allowlist;

    public ProfanityFilter() {
        this.profanity = Dictionary.load(BLOCKLIST_PATH);
        this.reserved = Dictionary.load(RESERVED_PATH);
        this.allowlist = buildTrie(Dictionary.load(ALLOWLIST_PATH).partial());
    }

    // 금칙어나 운영자 사칭 예약어가 포함되어 있으면 true
    public boolean containsBannedWord(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = normalize(text);

        // 부분 일치 검사 대상: 공백·특수문자를 뺀 문자열 (숫자 포함 / 숫자 제거)
        Set<String> compacts = new LinkedHashSet<>();
        compacts.add(compact(normalized, true));
        compacts.add(compact(normalized, false));

        // 단어 단위 검사 대상: 입력 전체 + 공백·문장부호로 나뉜 각 단어
        Set<String> words = new HashSet<>(compacts);
        for (String token : WORD_BOUNDARY.split(normalized)) {
            words.add(compact(token, true));
            words.add(compact(token, false));
        }

        return matches(reserved, compacts, words) || matches(profanity, compacts, words);
    }

    private boolean matches(Dictionary dictionary, Set<String> compacts, Set<String> words) {
        for (String word : words) {
            if (dictionary.wholeWords().contains(word)) {
                return true;
            }
        }
        for (String compact : compacts) {
            if (hasMatchOutsideAllowlist(dictionary.partialTrie(), compact)) {
                return true;
            }
        }
        return false;
    }

    // 화이트리스트 단어 안에 완전히 포함된 매치는 제외한다. (예: "시발점" 안의 "시발")
    private boolean hasMatchOutsideAllowlist(Trie trie, String text) {
        Collection<Emit> banned = trie.parseText(text);
        if (banned.isEmpty()) {
            return false;
        }

        Collection<Emit> allowed = allowlist.parseText(text);
        for (Emit hit : banned) {
            boolean covered = false;
            for (Emit allow : allowed) {
                if (allow.getStart() <= hit.getStart() && hit.getEnd() <= allow.getEnd()) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                return true;
            }
        }
        return false;
    }

    // 유니코드 정규화(NFC) → 앞뒤 공백 제거 → 소문자화 → 연속 공백을 하나로
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC)
                .strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    // 글자(와 숫자)만 남긴다.
    private static String compact(String text, boolean keepDigits) {
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints()
                .filter(cp -> Character.isLetter(cp) || (keepDigits && Character.isDigit(cp)))
                .forEach(sb::appendCodePoint);
        return sb.toString();
    }

    private static Trie buildTrie(Collection<String> keywords) {
        Trie.TrieBuilder builder = Trie.builder();
        keywords.forEach(builder::addKeyword);
        return builder.build();
    }

    // partial: 부분 일치 단어, wholeWords: 단어 단위('=') 단어
    private record Dictionary(Set<String> partial, Set<String> wholeWords, Trie partialTrie) {

        static Dictionary load(String path) {
            Set<String> partial = new HashSet<>();
            Set<String> wholeWords = new HashSet<>();

            for (String line : readLines(path)) {
                String entry = line.strip();
                if (entry.isEmpty() || entry.startsWith(COMMENT_PREFIX)) {
                    continue;
                }

                boolean wholeWord = entry.startsWith(WHOLE_WORD_PREFIX);
                String word = compact(normalize(wholeWord ? entry.substring(1) : entry), true);
                if (word.isEmpty()) {
                    continue;
                }
                (wholeWord ? wholeWords : partial).add(word);
            }

            return new Dictionary(Set.copyOf(partial), Set.copyOf(wholeWords), buildTrie(partial));
        }

        private static List<String> readLines(String path) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            } catch (IOException e) {
                throw new UncheckedIOException("금칙어 사전을 읽을 수 없습니다: " + path, e);
            }
        }
    }
}
