package com.kiri.server.domain.taste.caculator;

import com.kiri.server.domain.taste.dto.TasteSourceDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class FamilyTasteScoreCalculator {

    private static final int SCALE = 10;

    public BigDecimal calculate(List<TasteSourceDTO.MemberScore> memberScores) {

        if (memberScores == null || memberScores.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalScore = memberScores.stream().map(TasteSourceDTO.MemberScore::total).reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalScore.divide(BigDecimal.valueOf(memberScores.size()), SCALE, RoundingMode.HALF_UP);
    }
}
