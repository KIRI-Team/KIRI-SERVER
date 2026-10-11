package com.kiri.server.domain.question.event;

public record PokeEvent(
        Long senderId,
        Long receiverId
) {
}
