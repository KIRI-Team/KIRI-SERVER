package com.kiri.server.domain.image.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ImageReqDTO {
    public record PresignedUrl(
            @NotBlank(message = "contentType은 필수입니다.")
            String contentType,

            @NotNull(message = "contentLength는 필수입니다.")
            @Positive(message = "contentLength는 1 이상이어야 합니다.")
            Long contentLength
    ){}
}
