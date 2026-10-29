package com.kiri.server.domain.family.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class FamilyReqDTO {

    // 가족 공간 생성
    public record Create(
            @NotBlank(message = "가족 이름은 필수입니다.")
            @Size(min = 2, max = 15, message = "가족 이름은 2~15자여야 합니다.")
            String name
    ) {}

    public record UpdateName(
            @NotBlank(message = "가족 이름은 필수입니다.")
            @Size(min = 2, max = 15, message = "가족 이름은 2~15자여야 합니다.")
            @Pattern(
                    regexp = "^[가-힣a-zA-Z0-9 ]*$",
                    message = "가족 이름은 한글, 영문, 숫자 및 띄어쓰기만 사용할 수 있습니다."
            )
            String name
    ) {}
}
