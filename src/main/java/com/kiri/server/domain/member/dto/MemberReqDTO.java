package com.kiri.server.domain.member.dto;

import com.kiri.server.domain.member.enums.FamilyRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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

    // 닉네임 설정
    public record Nickname(
            @NotBlank(message = "닉네임은 필수입니다.")
            @Size(min = 2, max = 12, message = "닉네임은 2~12자로 입력해주세요.")
            @Pattern(
                    regexp = "^[가-힣a-zA-Z0-9]*$",
                    message = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다."
            )
            String nickname
    ) {}

    // 토큰 재발급
    public record TokenReissue(
            @NotBlank(message = "refreshToken은 필수입니다.")
            String refreshToken
    ) {}

    // 가족 역할 선택
    public record SelectFamilyRole(
            @NotNull(message = "가족 역할은 필수입니다.")
            FamilyRole familyRole
    ) {}
}
