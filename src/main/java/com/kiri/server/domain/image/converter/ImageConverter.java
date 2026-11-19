package com.kiri.server.domain.image.converter;

import com.kiri.server.domain.image.dto.ImageResDTO;

import java.time.LocalDateTime;

public class ImageConverter {

    public static ImageResDTO.PresignedUrl toPresignedUrl(String presignedUrl, String imageKey, LocalDateTime expiresAt){
        return ImageResDTO.PresignedUrl.builder()
                .presignedUrl(presignedUrl)
                .imageKey(imageKey)
                .expiresAt(expiresAt)
                .build();
    }
}
