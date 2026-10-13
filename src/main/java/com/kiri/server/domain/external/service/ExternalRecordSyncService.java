package com.kiri.server.domain.external.service;

import com.kiri.server.domain.external.client.tourapi.dto.TourApiResDTO;
import com.kiri.server.domain.external.converter.ExternalRecordConverter;
import com.kiri.server.domain.external.converter.TourApiConverter;
import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.SyncDecision;
import com.kiri.server.domain.external.repository.ExternalRecordHistoryRepository;
import com.kiri.server.domain.external.repository.ExternalRecordRepository;
import com.kiri.server.domain.mission.generator.CategoryClassifier;
import com.kiri.server.domain.mission.generator.MissionDraftFactory;
import com.kiri.server.domain.mission.service.MissionGenerationService;
import lombok.RequiredArgsConstructor;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExternalRecordSyncService {

    private static final ExternalSource SOURCE = ExternalSource.TOUR_API;

    private final ExternalRecordRepository externalRecordRepository;
    private final ExternalRecordHistoryRepository externalRecordHistoryRepository;
    private final MissionDraftFactory missionDraftFactory;
    private final MissionGenerationService missionGenerationService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SyncDecision decide(TourApiResDTO.SyncItem item) {
        Optional<ExternalRecord> record = find(item.contentId());
        if (!item.shown()) {
            return record.isPresent() ? SyncDecision.HIDE : SyncDecision.SKIP;
        }
        if (record.isEmpty()) {
            return CategoryClassifier.isIgnoredType(item.contentTypeId()) ? SyncDecision.SKIP : SyncDecision.FETCH;
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
            if (!record.isChanged(contentHash)) {
                record.updateOrigin(rawData, contentHash, item.modifiedAt(), sourceUrl, now);
                return;
            }
            externalRecordHistoryRepository.save(ExternalRecordConverter.toHistory(record));
            record.updateOrigin(rawData, contentHash, item.modifiedAt(), sourceUrl, now);
        }

        NormalizedContent content = TourApiConverter.toNormalizedContent(
                item, details.common(), details.intro(), details.infos());
        missionGenerationService.apply(record, missionDraftFactory.create(content), now.toLocalDate());
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
