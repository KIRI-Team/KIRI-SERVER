package com.kiri.server.domain.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

public class FamilyResDTO {

    @Builder
    @Schema(name = "FamilyCreateResponse", description = "가족 공간 생성 응답")
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
