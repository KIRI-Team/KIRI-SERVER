package com.kiri.server.domain.image.service;

import com.kiri.server.domain.image.converter.ImageConverter;
import com.kiri.server.domain.image.dto.ImageReqDTO;
import com.kiri.server.domain.image.dto.ImageResDTO;
import com.kiri.server.domain.image.enums.ImageContentType;
import com.kiri.server.domain.image.event.ImageConfirmedEvent;
import com.kiri.server.domain.image.exception.ImageException;
import com.kiri.server.domain.image.exception.code.ImageErrorCode;
import com.kiri.server.global.s3.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private static final String PENDING_KEY_FORMAT = "pending/user/%d/%s.%s";
    private static final String PENDING_PREFIX = "pending/user/";
    private static final String ORIGINAL_PREFIX = "user/original/";
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("[0-9a-f-]{36}\\.(jpg|png|webp)");
    private static final String CACHE_CONTROL = "max-age=31536000, immutable";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ImageResDTO.PresignedUrl issuePresignedUrl(Long memberId, ImageReqDTO.PresignedUrl dto) {
        ImageContentType contentType = ImageContentType.from(dto.contentType());
        if (dto.contentLength() > s3Properties.maxUploadSize().toBytes()) {
            throw new ImageException(ImageErrorCode.IMAGE_TOO_LARGE);
        }

        String imageKey = PENDING_KEY_FORMAT.formatted(memberId, UUID.randomUUID(), contentType.getExtension());

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(imageKey)
                .contentType(dto.contentType())
                .contentLength(dto.contentLength())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(s3Properties.presignedUrlExpiration())
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned;

        try {
            presigned = s3Presigner.presignPutObject(presignRequest);
        } catch (SdkException e) {
            throw new ImageException(ImageErrorCode.STORAGE_ERROR, e);
        }

        LocalDateTime expiresAt = LocalDateTime.ofInstant(presigned.expiration(), clock.getZone());
        return ImageConverter.toPresignedUrl(presigned.url().toString(), imageKey, expiresAt);
    }

    public String confirmUpload(Long memberId, String pendingKey) {
        String fileName = extractFileName(memberId, pendingKey);
        HeadObjectResponse head = headObject(pendingKey);

        ImageContentType contentType = ImageContentType.from(head.contentType());
        if (head.contentLength() > s3Properties.maxUploadSize().toBytes()) {
            throw new ImageException(ImageErrorCode.IMAGE_TOO_LARGE);
        }

        String originalKey = ORIGINAL_PREFIX + memberId + "/" + fileName;

        CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                .sourceBucket(s3Properties.bucket())
                .sourceKey(pendingKey)
                .destinationBucket(s3Properties.bucket())
                .destinationKey(originalKey)
                .metadataDirective(MetadataDirective.REPLACE)
                .contentType(contentType.getMimeType())
                .cacheControl(CACHE_CONTROL)
                .build();

        try {
            s3Client.copyObject(copyRequest);
        } catch (SdkException e) {
            throw new ImageException(ImageErrorCode.STORAGE_ERROR, e);
        }

        eventPublisher.publishEvent(new ImageConfirmedEvent(pendingKey, originalKey));
        return originalKey;
    }

    public void deleteImage(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(key)
                    .build());
        } catch (SdkException e) {
            log.warn("이미지 삭제 실패 key={}", key, e);
        }
    }

    private String extractFileName(Long memberId, String pendingKey) {
        String prefix = PENDING_PREFIX + memberId + "/";
        if (pendingKey == null || !pendingKey.startsWith(prefix)) {
            throw new ImageException(ImageErrorCode.INVALID_IMAGE_KEY);
        }
        String fileName = pendingKey.substring(prefix.length());
        if (!FILE_NAME_PATTERN.matcher(fileName).matches()){
            throw new ImageException(ImageErrorCode.INVALID_IMAGE_KEY);
        }
        return fileName;
    }

    private HeadObjectResponse headObject(String key) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(key)
                    .build());
        }catch (S3Exception e) {
            if (e.statusCode()==404){
                throw new ImageException(ImageErrorCode.IMAGE_NOT_UPLOADED, e);
            }
            throw new ImageException(ImageErrorCode.STORAGE_ERROR, e);
        }catch (SdkException e){
            throw new ImageException(ImageErrorCode.STORAGE_ERROR, e);
        }
    }
}
