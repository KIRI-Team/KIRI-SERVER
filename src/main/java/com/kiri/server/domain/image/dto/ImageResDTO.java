package com.kiri.server.domain.image.dto;

import lombok.Builder;

import java.time.LocalDateTime;

public class ImageResDTO {
    @Builder
    public record PresignedUrl(
            String presignedUrl,
            String imageKey,
            LocalDateTime expiresAt
    ){}
}
