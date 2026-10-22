package com.kiri.server.domain.family.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FamilyReqDTO {

    // 가족 공간 생성
    public record Create(
            @NotBlank(message = "가족 이름은 필수입니다.")
            @Size(min = 2, max = 20, message = "가족 이름은 2~20자여야 합니다.")
            String name
    ) {}
}
