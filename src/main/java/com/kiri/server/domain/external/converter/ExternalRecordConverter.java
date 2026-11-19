package com.kiri.server.domain.external.converter;

import com.kiri.server.domain.external.entity.ExternalRecord;
import com.kiri.server.domain.external.entity.ExternalRecordHistory;
import com.kiri.server.domain.external.enums.ExternalSource;
import com.kiri.server.domain.external.enums.ProcessStatus;

import java.time.LocalDateTime;

public class ExternalRecordConverter {

    public static ExternalRecord toExternalRecord(ExternalSource source, String externalId, String sourceUrl,
                                                  String rawData, String contentHash,
                                                  LocalDateTime originModifiedAt, LocalDateTime syncedAt) {
        return ExternalRecord.builder()
                .externalSource(source)
                .externalId(externalId)
                .sourceUrl(sourceUrl)
                .processStatus(ProcessStatus.REVIEW_REQUIRED)
                .rawData(rawData)
                .contentHash(contentHash)
                .originModifiedAt(originModifiedAt)
                .syncedAt(syncedAt)
                .build();
    }

    public static ExternalRecordHistory toHistory(ExternalRecord record) {
        return ExternalRecordHistory.builder()
                .externalRecord(record)
                .rawData(record.getRawData())
                .contentHash(record.getContentHash())
                .build();
    }
}
