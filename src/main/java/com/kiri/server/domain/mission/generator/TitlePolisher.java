package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.external.dto.NormalizedContent;

public interface TitlePolisher {

    String polish(String ruleTitle, NormalizedContent content);
}
