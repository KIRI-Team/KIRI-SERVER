package com.kiri.server.domain.image.event;

public record ImageConfirmedEvent(
        String pendingKey,
        String originalKey
) {
}
