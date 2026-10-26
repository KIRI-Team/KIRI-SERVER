package com.kiri.server.domain.external.service;

import com.kiri.server.domain.external.client.tourapi.dto.TourApiResDTO;
import com.kiri.server.domain.external.converter.ExternalRecordConverter;
import com.kiri.server.domain.external.converter.TourApiConverter;
import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.ProcessStatus;
import com.kiri.server.domain.external.enums.SyncDecision;
import com.kiri.server.domain.external.repository.ExternalRecordHistoryRepository;
import com.kiri.server.domain.external.repository.ExternalRecordRepository;
import com.kiri.server.domain.mission.generator.CategoryClassifier;
import com.kiri.server.domain.mission.generator.MissionDraftFactory;
import com.kiri.server.domain.mission.service.MissionGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalRecordSyncService {

    private static final ExternalSource SOURCE = ExternalSource.TOUR_API;
    private static final int MISSING_THRESHOLD = 2;
    private static final int MISSING_GUARD_MIN = 100;
    private static final double MISSING_GUARD_RATIO = 0.1;

    private final ExternalRecordRepository externalRecordRepository;
    private final ExternalRecordHistoryRepository externalRecordHistoryRepository;
    private final MissionDraftFactory missionDraftFactory;
    private final MissionGenerationService missionGenerationService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SyncDecision decide(TourApiResDTO.SyncItem item) {
        Optional<ExternalRecord> record = find(item.contentId());
        if (record.isPresent() && record.get().isIgnored()) {
            return SyncDecision.UNCHANGED;
        }
        if (!item.shown()) {
            return record.isPresent() ? SyncDecision.HIDE : SyncDecision.SKIP;
        }
        if (record.isEmpty()) {
            return CategoryClassifier.isIgnored(item.contentTypeId(), item.lclsSystm2(), item.lclsSystm3())
                    ? SyncDecision.SKIP : SyncDecision.FETCH;
        }
        if (record.get().isWaitingForOrigin()) {
            return SyncDecision.FETCH;
        }
        return record.get().isSameOrigin(item.modifiedAt()) ? SyncDecision.UNCHANGED : SyncDecision.FETCH;
    }

    @Transactional
    public void hide(TourApiResDTO.SyncItem item, LocalDateTime now) {
        find(item.contentId()).ifPresent(record -> {
            missionGenerationService.hide(record);
            record.markSynced(now);
        });
    }

    @Transactional
    public void touch(TourApiResDTO.SyncItem item, LocalDateTime now) {
        find(item.contentId()).ifPresent(record -> record.markSynced(now));
    }

    @Transactional
    public void apply(TourApiResDTO.SyncItem item, TourApiResDTO.Details details, LocalDateTime now) {
        String rawData = toRawData(item, details);
        String contentHash = sha256(rawData);
        String sourceUrl = TourApiConverter.toSourceUrl(details.common());

        Optional<ExternalRecord> found = find(item.contentId());
        ExternalRecord record;
        if (found.isEmpty()) {
            record = externalRecordRepository.save(ExternalRecordConverter.toExternalRecord(
                    SOURCE, item.contentId(), sourceUrl, rawData, contentHash, item.modifiedAt(), now));
        } else {
            record = found.get();
            boolean changed = record.isChanged(contentHash);
            if (changed) {
                externalRecordHistoryRepository.save(ExternalRecordConverter.toHistory(record));
            }
            boolean recheck = changed || record.isWaitingForOrigin();
            record.updateOrigin(rawData, contentHash, item.modifiedAt(), sourceUrl, now);
            if (!recheck || record.isIgnored()) {
                return;
            }
        }

        NormalizedContent content = TourApiConverter.toNormalizedContent(
                item, details.common(), details.intro(), details.infos());
        missionGenerationService.apply(record, missionDraftFactory.create(content), now.toLocalDate());
    }

    @Transactional
    public int reconcileMissing(Set<String> seenIds) {
        List<ExternalRecord> records =
                externalRecordRepository.findAllByExternalSourceAndProcessStatusNot(SOURCE, ProcessStatus.IGNORED);
        List<ExternalRecord> missing = records.stream()
                .filter(record -> !seenIds.contains(record.getExternalId()))
                .toList();
        if (missing.size() > Math.max(MISSING_GUARD_MIN, records.size() * MISSING_GUARD_RATIO)) {
            log.error("[TourAPI] 전체 점검에서 원본 {}건 중 {}건이 목록에 없어 비교를 건너뜁니다.",
                    records.size(), missing.size());
            return 0;
        }

        records.stream()
                .filter(record -> seenIds.contains(record.getExternalId()) && record.getMissingCount() > 0)
                .forEach(ExternalRecord::markSeen);
        int flagged = 0;
        for (ExternalRecord record : missing) {
            if (record.markMissing() >= MISSING_THRESHOLD && !record.isWaitingForOrigin()) {
                missionGenerationService.hide(record);
                flagged++;
            }
        }
        return flagged;
    }

    private Optional<ExternalRecord> find(String contentId) {
        return externalRecordRepository.findByExternalSourceAndExternalId(SOURCE, contentId);
    }

    private String toRawData(TourApiResDTO.SyncItem item, TourApiResDTO.Details details) {
        ObjectNode raw = objectMapper.createObjectNode();
        raw.set("item", orNull(item.raw()));
        raw.set("common", orNull(details.common()));
        raw.set("intro", orNull(details.intro()));
        raw.putArray("info").addAll(details.infos());
        return objectMapper.writeValueAsString(raw);
    }

    private static JsonNode orNull(JsonNode node) {
        return node == null ? NullNode.getInstance() : node;
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
