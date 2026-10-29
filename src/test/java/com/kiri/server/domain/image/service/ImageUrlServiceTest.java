package com.kiri.server.domain.image.service;

import com.kiri.server.global.cloudfront.CloudFrontProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ImageUrlServiceTest {

    private static final String CDN_BASE_URL = "https://test.cloudfront.net";
    private static final String KEY_PAIR_ID = "KTESTKEYPAIRID";
    private static final String USER_KEY = "user/original/1/3f2b8c1e-7d4a-4f6b-9a2e-1c5d8e9f0a7b.jpg";

    // 서명 계산은 로컬에서 끝나므로 테스트용 RSA 키를 직접 생성
    private static final KeyPair KEY_PAIR = generateKeyPair();

    private ImageUrlService imageUrlService;

    @BeforeEach
    void setUp() {
        setUpAt("2026-10-30T05:20:00Z");
    }

    @Test
    @DisplayName("user/ 키는 CDN 주소에 만료·서명·키 페어 ID를 붙인 Signed URL로 변환한다.")
    void toSignedUrl() {
        String url = imageUrlService.toUrl(USER_KEY);

        assertThat(url)
                .startsWith(CDN_BASE_URL + "/" + USER_KEY + "?")
                .contains("Signature=")
                .contains("Key-Pair-Id=" + KEY_PAIR_ID);
        // 05:20 → 정각 05:00 + 2시간 = 07:00
        assertThat(url).contains("Expires=" + Instant.parse("2026-10-30T07:00:00Z").getEpochSecond());
    }

    @Test
    @DisplayName("같은 시간대에 만든 URL은 같고, 정각이 지나면 달라진다.")
    void sameUrlWithinHour() {
        String first = imageUrlService.toUrl(USER_KEY);

        setUpAt("2026-10-30T05:59:59Z");
        String sameHour = imageUrlService.toUrl(USER_KEY);

        setUpAt("2026-10-30T06:00:00Z");
        String nextHour = imageUrlService.toUrl(USER_KEY);

        assertThat(sameHour).isEqualTo(first);
        assertThat(nextHour).isNotEqualTo(first);
    }

    @Test
    @DisplayName("public/ 키는 서명 없이 CDN 주소만 붙인다.")
    void toPublicUrl() {
        assertThat(imageUrlService.toUrl("public/mission/default.png"))
                .isEqualTo(CDN_BASE_URL + "/public/mission/default.png");
    }

    @Test
    @DisplayName("키가 없으면 null을 반환한다.")
    void toUrlNull() {
        assertThat(imageUrlService.toUrl(null)).isNull();
    }

    private void setUpAt(String instant) {
        CloudFrontProperties properties = new CloudFrontProperties(CDN_BASE_URL, KEY_PAIR_ID, null, Duration.ofHours(2));
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul"));
        imageUrlService = new ImageUrlService(CloudFrontUtilities.create(), KEY_PAIR.getPrivate(), properties, clock);
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
