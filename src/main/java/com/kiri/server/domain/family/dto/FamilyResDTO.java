package com.kiri.server.domain.family.dto;

import lombok.Builder;

import java.time.LocalDateTime;

public class FamilyResDTO {

    @Builder
    public record Create(
            Long familyId,
            String name,
            String inviteCode,
            String inviteUrl,
            LocalDateTime inviteExpiresAt
    ) {}

    @Builder
    public record Invite(
            String inviteCode,
            String inviteUrl,
            LocalDateTime inviteExpiresAt
    ) {}

    @Builder
    public record UpdateName(
            Long familyId,
            String name
    ) {}
}
