package com.kiri.server.domain.image.service;

import com.kiri.server.domain.image.dto.ImageReqDTO;
import com.kiri.server.domain.image.dto.ImageResDTO;
import com.kiri.server.domain.image.event.ImageConfirmedEvent;
import com.kiri.server.domain.image.exception.ImageException;
import com.kiri.server.domain.image.exception.code.ImageErrorCode;
import com.kiri.server.global.s3.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final String BUCKET = "kiri-test-images";
    private static final String FILE_NAME = "3f2b8c1e-7d4a-4f6b-9a2e-1c5d8e9f0a7b.jpg";
    private static final String PENDING_KEY = "pending/user/1/" + FILE_NAME;
    private static final String ORIGINAL_KEY = "user/original/1/" + FILE_NAME;
    private static final long MAX_BYTES = DataSize.ofMegabytes(10).toBytes();

    @Mock
    private S3Client s3Client;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ImageService imageService;

    // 서명 계산은 네트워크 없이 로컬에서 끝나므로 실제 Presigner 사용
    private final S3Presigner s3Presigner = S3Presigner.builder()
            .region(Region.AP_NORTHEAST_2)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATEST", "secret")))
            .build();

    private final Clock clock = Clock.fixed(Instant.now(), ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void setUp() {
        S3Properties properties = new S3Properties("ap-northeast-2", BUCKET, Duration.ofMinutes(5), DataSize.ofMegabytes(10));
        imageService = new ImageService(s3Client, s3Presigner, properties, eventPublisher, clock);
    }

    @Test
    @DisplayName("본인 pending 경로에 Content-Type·Content-Length를 서명한 PUT URL을 발급한다.")
    void issuePresignedUrl() {
        ImageResDTO.PresignedUrl result = imageService.issuePresignedUrl(MEMBER_ID,
                new ImageReqDTO.PresignedUrl("image/png", 1024L));

        assertThat(result.imageKey()).matches("pending/user/1/[0-9a-f-]{36}\\.png");
        assertThat(result.presignedUrl())
                .contains(result.imageKey())
                .contains("X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost")
                .contains("X-Amz-Expires=300");
        // Presigner는 시스템 시각 기준으로 만료를 계산하므로 범위로 확인 (UTC로 바뀌면 9시간 차이로 실패)
        LocalDateTime now = LocalDateTime.now(clock);
        assertThat(result.expiresAt()).isBetween(now.plusMinutes(4), now.plusMinutes(6));
    }

    @Test
    @DisplayName("image/jpg로 요청해도 확장자는 jpg로 만든다.")
    void issuePresignedUrlWithAlias() {
        ImageResDTO.PresignedUrl result = imageService.issuePresignedUrl(MEMBER_ID,
                new ImageReqDTO.PresignedUrl("image/jpg", 1024L));

        assertThat(result.imageKey()).endsWith(".jpg");
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type이면 IMAGE400_1로 실패한다.")
    void issuePresignedUrlUnsupportedType() {
        assertThatThrownBy(() -> imageService.issuePresignedUrl(MEMBER_ID,
                new ImageReqDTO.PresignedUrl("image/gif", 1024L)))
                .isInstanceOf(ImageException.class)
                .extracting("errorCode")
                .isEqualTo(ImageErrorCode.UNSUPPORTED_CONTENT_TYPE);
    }

    @Test
    @DisplayName("최대 용량과 같으면 발급하고, 1바이트라도 넘으면 IMAGE400_3으로 실패한다.")
    void issuePresignedUrlSizeLimit() {
        assertThatCode(() -> imageService.issuePresignedUrl(MEMBER_ID,
                new ImageReqDTO.PresignedUrl("image/jpeg", MAX_BYTES)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> imageService.issuePresignedUrl(MEMBER_ID,
                new ImageReqDTO.PresignedUrl("image/jpeg", MAX_BYTES + 1)))
                .isInstanceOf(ImageException.class)
                .extracting("errorCode")
                .isEqualTo(ImageErrorCode.IMAGE_TOO_LARGE);
    }

    @Test
    @DisplayName("업로드를 확정하면 user/original로 메타데이터를 교체해 복사하고 이벤트를 발행한다.")
    void confirmUpload() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head("image/jpg", 1024L));

        String result = imageService.confirmUpload(MEMBER_ID, PENDING_KEY);

        ArgumentCaptor<CopyObjectRequest> captor = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3Client).copyObject(captor.capture());
        CopyObjectRequest copy = captor.getValue();
        assertThat(copy.sourceKey()).isEqualTo(PENDING_KEY);
        assertThat(copy.destinationKey()).isEqualTo(ORIGINAL_KEY);
        assertThat(copy.metadataDirective()).isEqualTo(MetadataDirective.REPLACE);
        assertThat(copy.contentType()).isEqualTo("image/jpeg");
        assertThat(copy.cacheControl()).isEqualTo("max-age=31536000, immutable");

        assertThat(result).isEqualTo(ORIGINAL_KEY);
        verify(eventPublisher).publishEvent(new ImageConfirmedEvent(PENDING_KEY, ORIGINAL_KEY));
    }

    @ParameterizedTest
    @DisplayName("본인 pending 경로가 아니거나 발급 형식과 다른 키면 IMAGE400_2로 실패한다.")
    @ValueSource(strings = {
            "pending/user/2/3f2b8c1e-7d4a-4f6b-9a2e-1c5d8e9f0a7b.jpg",
            "pending/user/11/3f2b8c1e-7d4a-4f6b-9a2e-1c5d8e9f0a7b.jpg",
            "user/original/1/3f2b8c1e-7d4a-4f6b-9a2e-1c5d8e9f0a7b.jpg",
            "pending/user/1/../2/3f2b8c1e-7d4a-4f6b-9a2e-1c5d8e9f0a7b.jpg",
            "pending/user/1/3f2b8c1e-7d4a-4f6b-9a2e-1c5d8e9f0a7b.gif",
            "pending/user/1/"
    })
    void confirmUploadInvalidKey(String key) {
        assertThatThrownBy(() -> imageService.confirmUpload(MEMBER_ID, key))
                .isInstanceOf(ImageException.class)
                .extracting("errorCode")
                .isEqualTo(ImageErrorCode.INVALID_IMAGE_KEY);

        verifyNoInteractions(s3Client, eventPublisher);
    }

    @Test
    @DisplayName("업로드되지 않은 키면 IMAGE404_1로 실패한다.")
    void confirmUploadNotUploaded() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().statusCode(404).build());

        assertThatThrownBy(() -> imageService.confirmUpload(MEMBER_ID, PENDING_KEY))
                .isInstanceOf(ImageException.class)
                .extracting("errorCode")
                .isEqualTo(ImageErrorCode.IMAGE_NOT_UPLOADED);

        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    @DisplayName("S3 조회 중 404 외의 오류가 나면 IMAGE500_1로 실패한다.")
    void confirmUploadHeadError() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(503).build());

        assertThatThrownBy(() -> imageService.confirmUpload(MEMBER_ID, PENDING_KEY))
                .isInstanceOf(ImageException.class)
                .extracting("errorCode")
                .isEqualTo(ImageErrorCode.STORAGE_ERROR);
    }

    @Test
    @DisplayName("저장된 객체가 최대 용량을 넘으면 복사하지 않고 IMAGE400_3으로 실패한다.")
    void confirmUploadTooLarge() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head("image/jpeg", MAX_BYTES + 1));

        assertThatThrownBy(() -> imageService.confirmUpload(MEMBER_ID, PENDING_KEY))
                .isInstanceOf(ImageException.class)
                .extracting("errorCode")
                .isEqualTo(ImageErrorCode.IMAGE_TOO_LARGE);

        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    @DisplayName("복사에 실패하면 IMAGE500_1로 실패하고 이벤트를 발행하지 않는다.")
    void confirmUploadCopyFailed() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(head("image/jpeg", 1024L));
        when(s3Client.copyObject(any(CopyObjectRequest.class)))
                .thenThrow(SdkClientException.create("연결 실패"));

        assertThatThrownBy(() -> imageService.confirmUpload(MEMBER_ID, PENDING_KEY))
                .isInstanceOf(ImageException.class)
                .extracting("errorCode")
                .isEqualTo(ImageErrorCode.STORAGE_ERROR);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("이미지 삭제에 실패해도 예외를 던지지 않는다.")
    void deleteImageFailed() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("연결 실패"));

        assertThatCode(() -> imageService.deleteImage(PENDING_KEY)).doesNotThrowAnyException();
    }

    private HeadObjectResponse head(String contentType, long contentLength) {
        return HeadObjectResponse.builder()
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
    }
}
