package com.kiri.server.domain.image.service;

import com.kiri.server.domain.image.converter.ImageConverter;
import com.kiri.server.domain.image.dto.ImageReqDTO;
import com.kiri.server.domain.image.dto.ImageResDTO;
import com.kiri.server.domain.image.enums.ImageContentType;
import com.kiri.server.domain.image.exception.ImageException;
import com.kiri.server.domain.image.exception.code.ImageErrorCode;
import com.kiri.server.global.s3.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageService {

    private static final String PENDING_KEY_FORMAT = "pending/user/%d/%s.%s";

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
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
}
