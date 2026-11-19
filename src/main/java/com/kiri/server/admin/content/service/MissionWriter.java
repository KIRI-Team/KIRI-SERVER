package com.kiri.server.admin.content.service;

import com.kiri.server.admin.common.AdminErrorCode;
import com.kiri.server.admin.common.AdminException;
import com.kiri.server.admin.content.dto.MissionFormDTO;
import com.kiri.server.domain.mission.entity.Mission;
import com.kiri.server.domain.mission.entity.MissionChecklistItem;
import com.kiri.server.domain.mission.entity.MissionContent;
import com.kiri.server.domain.mission.enums.ChecklistSource;
import com.kiri.server.domain.mission.enums.ItemType;
import com.kiri.server.domain.mission.enums.LockedField;
import com.kiri.server.domain.mission.repository.MissionChecklistItemRepository;
import com.kiri.server.domain.mission.repository.MissionRepository;
import com.kiri.server.domain.place.entity.Place;
import com.kiri.server.domain.place.entity.PlaceContent;
import com.kiri.server.domain.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// 운영자 폼을 미션·장소·체크리스트에 반영한다. 운영자가 바꾼 필드는 잠가서 외부 재수집이 덮어쓰지 않게 한다.
@Component
@RequiredArgsConstructor
public class MissionWriter {

    private final MissionRepository missionRepository;
    private final PlaceRepository placeRepository;
    private final MissionChecklistItemRepository checklistRepository;

    public record WriteResult(Mission mission, Set<LockedField> changedFields) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WriteResult createManual(MissionFormDTO.MissionForm form) {
        Place place = resolvePlace(form.place());
        Mission mission = missionRepository.save(Mission.createManual(toContent(form), place));
        saveChecklist(mission, form.checklist());
        return new WriteResult(mission, EnumSet.noneOf(LockedField.class));
    }

    // 검수 승인으로 새 미션을 만든다. suggestion과 다른 값은 운영자가 고친 값이므로 잠근다.
    @Transactional(propagation = Propagation.MANDATORY)
    public WriteResult createApproved(MissionFormDTO.MissionForm form, MissionFormDTO.MissionForm suggestion) {
        Place place = resolvePlace(form.place());
        Mission mission = missionRepository.save(Mission.createApproved(toContent(form), place));
        saveChecklist(mission, form.checklist());
        Set<LockedField> changed = EnumSet.noneOf(LockedField.class);
        if (suggestion != null) {
            Mission baseline = Mission.createApproved(toContent(suggestion), place);
            changed.addAll(baseline.diff(toContent(form)));
            if (!sameChecklist(suggestion.checklist(), form.checklist())) {
                changed.add(LockedField.CHECKLIST);
            }
        } else {
            changed.addAll(EnumSet.of(LockedField.TITLE, LockedField.DESCRIPTION, LockedField.CATEGORY,
                    LockedField.MISSION_TYPE, LockedField.PERIOD, LockedField.CHECKLIST));
        }
        mission.lock(changed);
        return new WriteResult(mission, changed);
    }

    // 기존 미션을 수정하고 바뀐 필드를 잠근다.
    @Transactional(propagation = Propagation.MANDATORY)
    public WriteResult update(Mission mission, MissionFormDTO.MissionForm form) {
        Place place = resolvePlace(form.place());
        Set<LockedField> changed = EnumSet.noneOf(LockedField.class);
        changed.addAll(mission.applyAdminContent(toContent(form), place));
        List<MissionChecklistItem> current = checklistRepository.findAllByMissionOrderByItemTypeAscDisplayOrderAsc(mission);
        if (!sameChecklist(toForms(current), form.checklist())) {
            checklistRepository.deleteAllByMissionInBulk(mission);
            saveChecklist(mission, form.checklist());
            changed.add(LockedField.CHECKLIST);
        }
        mission.lock(changed);
        return new WriteResult(mission, changed);
    }

    public MissionFormDTO.MissionForm toForm(Mission mission) {
        Place place = mission.getPlace();
        return MissionFormDTO.MissionForm.builder()
                .title(mission.getTitle())
                .description(mission.getDescription())
                .category(mission.getCategory())
                .missionType(mission.getMissionType())
                .indoorOutdoor(mission.getIndoorOutdoor())
                .startDate(mission.getStartDate())
                .endDate(mission.getEndDate())
                .openingTime(mission.getOpeningTime())
                .closingTime(mission.getClosingTime())
                .price(mission.getPrice())
                .durationMinMinutes(mission.getDurationMinMinutes())
                .durationMaxMinutes(mission.getDurationMaxMinutes())
                .walkingLevel(mission.getWalkingLevel())
                .imageUrl(mission.getImageUrl())
                .imageSource(mission.getImageSource())
                .reservationUrl(mission.getReservationUrl())
                .place(MissionFormDTO.PlaceForm.builder()
                        .placeId(place.getId())
                        .name(place.getName())
                        .placeType(place.getPlaceType())
                        .regionCode(place.getRegionCode())
                        .latitude(place.getLatitude())
                        .longitude(place.getLongitude())
                        .address(place.getAddress())
                        .openingTime(place.getOpeningTime())
                        .closingTime(place.getClosingTime())
                        .imageUrl(place.getImageUrl())
                        .imageSource(place.getImageSource())
                        .build())
                .checklist(toForms(checklistRepository.findAllByMissionOrderByItemTypeAscDisplayOrderAsc(mission)))
                .build();
    }

    private Place resolvePlace(MissionFormDTO.PlaceForm form) {
        PlaceContent content = PlaceContent.builder()
                .name(form.name().strip())
                .placeType(form.placeType())
                .regionCode(form.regionCode())
                .latitude(form.latitude())
                .longitude(form.longitude())
                .address(form.address())
                .openingTime(form.openingTime())
                .closingTime(form.closingTime())
                .imageUrl(form.imageUrl())
                .imageSource(form.imageSource())
                .build();
        if (form.placeId() == null) {
            return placeRepository.save(Place.create(content));
        }
        Place place = placeRepository.findById(form.placeId())
                .orElseThrow(() -> new AdminException(AdminErrorCode.TARGET_NOT_FOUND));
        place.applyAdminContent(content);
        return place;
    }

    private void saveChecklist(Mission mission, List<MissionFormDTO.ChecklistForm> items) {
        List<MissionChecklistItem> entities = new ArrayList<>();
        int prepareOrder = 0;
        int stepOrder = 0;
        for (MissionFormDTO.ChecklistForm item : items) {
            int order = item.itemType() == ItemType.PREPARE ? ++prepareOrder : ++stepOrder;
            entities.add(MissionChecklistItem.builder()
                    .mission(mission)
                    .itemType(item.itemType())
                    .source(ChecklistSource.OPERATOR)
                    .title(item.title().strip())
                    .description(item.description())
                    .displayOrder(order)
                    .build());
        }
        checklistRepository.saveAll(entities);
    }

    private static List<MissionFormDTO.ChecklistForm> toForms(List<MissionChecklistItem> items) {
        return items.stream()
                .map(item -> new MissionFormDTO.ChecklistForm(item.getItemType(), item.getTitle(), item.getDescription()))
                .toList();
    }

    private static boolean sameChecklist(List<MissionFormDTO.ChecklistForm> a, List<MissionFormDTO.ChecklistForm> b) {
        return normalize(a).equals(normalize(b));
    }

    private static List<String> normalize(List<MissionFormDTO.ChecklistForm> items) {
        List<String> prepare = new ArrayList<>();
        List<String> steps = new ArrayList<>();
        for (MissionFormDTO.ChecklistForm item : items) {
            String key = (item.title() == null ? "" : item.title().strip()) + "|" + (item.description() == null ? "" : item.description().strip());
            (item.itemType() == ItemType.PREPARE ? prepare : steps).add(key);
        }
        List<String> all = new ArrayList<>(prepare);
        all.add("--");
        all.addAll(steps);
        return all;
    }

    static MissionContent toContent(MissionFormDTO.MissionForm form) {
        return MissionContent.builder()
                .title(form.title().strip())
                .description(form.description() == null ? "" : form.description())
                .category(form.category())
                .missionType(form.missionType())
                .indoorOutdoor(form.indoorOutdoor())
                .startDate(form.startDate())
                .endDate(form.endDate())
                .openingTime(form.openingTime())
                .closingTime(form.closingTime())
                .price(form.price())
                .durationMinMinutes(form.durationMinMinutes())
                .durationMaxMinutes(form.durationMaxMinutes())
                .walkingLevel(form.walkingLevel())
                .imageUrl(blankToNull(form.imageUrl()))
                .imageSource(form.imageSource())
                .reservationUrl(blankToNull(form.reservationUrl()))
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
