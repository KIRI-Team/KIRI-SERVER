package com.kiri.server.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

public class MemberReqDTO {

    // 로그인
    public record Login(
            @NotBlank(message = "socialProvider는 필수입니다.")
            String socialProvider,

            @NotBlank(message = "socialProviderToken은 필수입니다.")
            String socialProviderToken,

            @NotBlank(message = "nonce는 필수입니다.")
            String nonce
    ) {}
}
