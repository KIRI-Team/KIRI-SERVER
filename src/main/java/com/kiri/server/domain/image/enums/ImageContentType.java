package com.kiri.server.domain.image.enums;

import com.kiri.server.domain.image.exception.ImageException;
import com.kiri.server.domain.image.exception.code.ImageErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum ImageContentType {
    JPEG("image/jpeg", "jpg", Set.of("image/jpg")),
    PNG("image/png", "png", Set.of()),
    WEBP("image/webp", "webp", Set.of());

    private final String mimeType;
    private final String extension;
    private final Set<String> aliases;

    public static ImageContentType from(String contentType) {
        return Arrays.stream(values())
                .filter(type -> type.matches(contentType))
                .findFirst()
                .orElseThrow(() -> new ImageException(ImageErrorCode.UNSUPPORTED_CONTENT_TYPE));
    }

    private boolean matches(String contentType) {
        if (mimeType.equalsIgnoreCase(contentType)) {
            return true;
        }
        return aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(contentType));
    }
}
