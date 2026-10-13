package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.external.dto.NormalizedContent;
import org.springframework.stereotype.Component;

@Component
public class RuleOnlyTitlePolisher implements TitlePolisher {

    @Override
    public String polish(String ruleTitle, NormalizedContent content) {
        return ruleTitle;
    }
}
