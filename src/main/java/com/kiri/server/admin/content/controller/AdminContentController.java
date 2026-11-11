package com.kiri.server.admin.content.controller;

import com.kiri.server.admin.common.AdminPageResponse;
import com.kiri.server.admin.common.AdminSuccessCode;
import com.kiri.server.admin.content.dto.ContentDTO;
import com.kiri.server.admin.content.dto.MissionFormDTO;
import com.kiri.server.admin.content.service.AdminMissionService;
import com.kiri.server.admin.content.service.AdminPlaceQuestionService;
import com.kiri.server.domain.image.dto.ImageReqDTO;
import com.kiri.server.domain.image.dto.ImageResDTO;
import com.kiri.server.domain.image.service.ImageService;
import com.kiri.server.domain.image.service.ImageUrlService;
import com.kiri.server.domain.mission.dto.MissionResDTO;
import com.kiri.server.domain.mission.enums.LockedField;
import com.kiri.server.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[관리자] 콘텐츠 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminContentController {

    private final AdminMissionService missionService;
    private final AdminPlaceQuestionService placeQuestionService;
    private final ImageService imageService;
    private final ImageUrlService imageUrlService;

    // ===== 미션 =====

    @Operation(summary = "미션 목록", description = "상태·카테고리·출처·유형·지역·키워드로 검색합니다.")
    @GetMapping("/missions")
    public ApiResponse<AdminPageResponse<ContentDTO.MissionItem>> searchMissions(@ModelAttribute ContentDTO.MissionCondition condition) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, missionService.search(condition));
    }

    @Operation(summary = "미션 상세", description = "편집 폼 값, 잠긴 필드, 연결 원본")
    @GetMapping("/missions/{missionId}")
    public ApiResponse<ContentDTO.MissionDetail> getMission(@PathVariable Long missionId) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, missionService.detail(missionId));
    }

    @Operation(summary = "미션 수동 등록", description = "초안(DRAFT)으로 저장합니다. place.placeId를 주면 기존 장소를, 없으면 새 장소를 만듭니다.")
    @PostMapping("/missions")
    public ApiResponse<ContentDTO.MissionDetail> createMission(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                               @RequestBody MissionFormDTO.MissionForm form) {
        return ApiResponse.onSuccess(AdminSuccessCode.CREATED, missionService.create(adminId, form));
    }

    @Operation(summary = "미션 수정", description = "공개 중(ACTIVE)인 미션은 사유가 필수입니다. 바뀐 필드는 잠겨서 외부 재수집이 덮어쓰지 않습니다. 체크리스트도 폼으로 함께 교체합니다.")
    @PatchMapping("/missions/{missionId}")
    public ApiResponse<ContentDTO.MissionDetail> updateMission(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                               @PathVariable Long missionId,
                                                               @RequestBody ContentDTO.UpdateMissionRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, missionService.update(adminId, missionId, request));
    }

    @Operation(summary = "미션 미리보기", description = "앱 미션 상세와 같은 응답 형태. 초안도 볼 수 있습니다.")
    @GetMapping("/missions/{missionId}/preview")
    public ApiResponse<MissionResDTO.Detail> preview(@PathVariable Long missionId) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, missionService.preview(missionId));
    }

    @Operation(summary = "초안 공개", description = "DRAFT → ACTIVE. 공개 필수값(소개, 필수 활동 2개 이상 등)을 검증합니다.")
    @PostMapping("/missions/{missionId}/publish")
    public ApiResponse<ContentDTO.MissionDetail> publish(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                         @PathVariable Long missionId) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, missionService.publish(adminId, missionId));
    }

    @Operation(summary = "미션 숨김", description = "사유 필수")
    @PostMapping("/missions/{missionId}/hide")
    public ApiResponse<ContentDTO.MissionDetail> hide(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                      @PathVariable Long missionId,
                                                      @RequestBody ContentDTO.ReasonRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, missionService.hide(adminId, missionId, request.reason()));
    }

    @Operation(summary = "숨긴 미션 복구", description = "HIDDEN → ACTIVE(기간이 지났으면 ENDED). 사유 필수")
    @PostMapping("/missions/{missionId}/restore")
    public ApiResponse<ContentDTO.MissionDetail> restore(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                         @PathVariable Long missionId,
                                                         @RequestBody ContentDTO.ReasonRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, missionService.restore(adminId, missionId, request.reason()));
    }

    @Operation(summary = "필드 잠금 해제", description = "해제하면 다음 외부 재수집 때 원본 값으로 돌아갑니다.")
    @DeleteMapping("/missions/{missionId}/locked-fields/{field}")
    public ApiResponse<ContentDTO.MissionDetail> unlock(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                        @PathVariable Long missionId,
                                                        @PathVariable LockedField field) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, missionService.unlock(adminId, missionId, field));
    }

    // ===== 장소 =====

    @Operation(summary = "장소 목록")
    @GetMapping("/places")
    public ApiResponse<AdminPageResponse<ContentDTO.PlaceItem>> searchPlaces(@ModelAttribute ContentDTO.PlaceCondition condition) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, placeQuestionService.searchPlaces(condition));
    }

    @Operation(summary = "장소 상세")
    @GetMapping("/places/{placeId}")
    public ApiResponse<ContentDTO.PlaceItem> getPlace(@PathVariable Long placeId) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, placeQuestionService.getPlace(placeId));
    }

    @Operation(summary = "장소 등록")
    @PostMapping("/places")
    public ApiResponse<ContentDTO.PlaceItem> createPlace(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                         @RequestBody MissionFormDTO.PlaceForm form) {
        return ApiResponse.onSuccess(AdminSuccessCode.CREATED, placeQuestionService.createPlace(adminId, form));
    }

    @Operation(summary = "장소 수정", description = "바뀐 필드는 잠겨서 외부 재수집이 덮어쓰지 않습니다.")
    @PatchMapping("/places/{placeId}")
    public ApiResponse<ContentDTO.PlaceItem> updatePlace(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                         @PathVariable Long placeId,
                                                         @RequestBody MissionFormDTO.PlaceForm form) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, placeQuestionService.updatePlace(adminId, placeId, form));
    }

    // ===== 질문 =====

    @Operation(summary = "질문 목록", description = "assignedCount는 가족에게 배정된 횟수입니다.")
    @GetMapping("/questions")
    public ApiResponse<AdminPageResponse<ContentDTO.QuestionItem>> searchQuestions(@ModelAttribute ContentDTO.QuestionCondition condition) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, placeQuestionService.searchQuestions(condition));
    }

    @Operation(summary = "질문 등록")
    @PostMapping("/questions")
    public ApiResponse<ContentDTO.QuestionItem> createQuestion(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                               @Valid @RequestBody ContentDTO.QuestionRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.CREATED, placeQuestionService.createQuestion(adminId, request));
    }

    @Operation(summary = "질문 수정", description = "이미 가족에게 배정된 질문은 문구를 바꿀 수 없습니다(ADMIN409_4). 새 질문을 만들고 기존 질문을 중지하세요.")
    @PatchMapping("/questions/{questionId}")
    public ApiResponse<ContentDTO.QuestionItem> updateQuestion(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                               @PathVariable Long questionId,
                                                               @Valid @RequestBody ContentDTO.QuestionRequest request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, placeQuestionService.updateQuestion(adminId, questionId, request));
    }

    @Operation(summary = "질문 중지", description = "새로 배정하지 않습니다. 이미 배정된 기록은 유지됩니다.")
    @PostMapping("/questions/{questionId}/deactivate")
    public ApiResponse<ContentDTO.QuestionItem> deactivate(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                           @PathVariable Long questionId) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, placeQuestionService.setQuestionActive(adminId, questionId, false));
    }

    @Operation(summary = "질문 다시 사용")
    @PostMapping("/questions/{questionId}/activate")
    public ApiResponse<ContentDTO.QuestionItem> activate(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                         @PathVariable Long questionId) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, placeQuestionService.setQuestionActive(adminId, questionId, true));
    }

    // ===== 이미지 =====

    @Operation(summary = "콘텐츠 이미지 업로드 URL 발급", description = "받은 presignedUrl로 PUT 업로드한 뒤 confirm을 호출하세요.")
    @PostMapping("/images/presigned-url")
    public ApiResponse<ImageResDTO.PresignedUrl> presign(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                         @Valid @RequestBody ImageReqDTO.PresignedUrl request) {
        return ApiResponse.onSuccess(AdminSuccessCode.OK, imageService.issueAdminPresignedUrl(adminId, request));
    }

    @Operation(summary = "콘텐츠 이미지 업로드 확정", description = "공개 경로로 옮기고 미션·장소 imageUrl에 넣을 URL을 돌려줍니다.")
    @PostMapping("/images/confirm")
    public ApiResponse<ContentDTO.ConfirmedImage> confirm(@Parameter(hidden = true) @AuthenticationPrincipal Long adminId,
                                                          @Valid @RequestBody ContentDTO.ConfirmImageRequest request) {
        String key = imageService.confirmAdminUpload(adminId, request.imageKey());
        return ApiResponse.onSuccess(AdminSuccessCode.OK, new ContentDTO.ConfirmedImage(key, imageUrlService.toUrl(key)));
    }
}
