package com.kiri.server.domain.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public class FamilyReqDTO {

    // 가족 공간 생성, 가족 이름 설정
    @Schema(name = "FamilyNameRequest", description = "가족 이름 요청")
    public record Name(
            @NotBlank(message = "가족 이름은 필수입니다.")
            @Size(min = 2, max = 15, message = "가족 이름은 2~15자여야 합니다.")
            @Pattern(
                    regexp = "^[가-힣a-zA-Z0-9 ]*$",
                    message = "가족 이름은 한글, 영문, 숫자 및 띄어쓰기만 사용할 수 있습니다."
            )
            @Schema(description = "가족명 (한글·영문·숫자·띄어쓰기만 허용, 앞뒤 공백 제거 및 연속 공백을 1개로 정리한 뒤 2~15자)", example = "끼리네 가족")
            String name
    ) {
        public Name {
            name = name == null ? null : name.strip().replaceAll("\\s+", " ");
        }
    }

    // 가족 주소 설정
    public record UpdateAddress(
            @NotBlank(message = "우편번호는 필수입니다.")
            @Pattern(regexp = "^\\d{5}$", message = "우편번호는 5자리 숫자여야 합니다.")
            String postalCode,

            @NotBlank(message = "기본 주소는 필수입니다.")
            String address,

            String detailAddress,

            @NotBlank(message = "지역 코드는 필수입니다.")
            String regionCode,

            @NotNull(message = "위도는 필수입니다.")
            @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
            @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
            BigDecimal latitude,

            @NotNull(message = "경도는 필수입니다.")
            @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
            @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
            BigDecimal longitude
    ) {}

    // 가족 초대 코드 입력
    public record Join(
            @NotBlank(message = "초대 코드는 필수입니다.")
            @Pattern(regexp = "^[A-Z0-9]{6}$", message = "초대 코드는 영문 대문자와 숫자 6자리여야 합니다.")
            String inviteCode
    ) {}
}
