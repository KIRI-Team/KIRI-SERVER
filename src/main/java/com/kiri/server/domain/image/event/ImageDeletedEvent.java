package com.kiri.server.domain.image.event;

import java.util.List;

public record ImageDeletedEvent(
        List<String> imageKeys
) {
}
