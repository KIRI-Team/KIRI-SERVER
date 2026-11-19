package com.kiri.server.domain.taste.caculator;

import com.kiri.server.domain.taste.dto.TasteSourceDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PreferenceWeightCalculator {

    private static final BigDecimal TASTE_TEST_WEIGHT = new BigDecimal("0.8");
    private static final BigDecimal BOTH_RATIO = new BigDecimal("0.35");

    public TasteSourceDTO.PreferenceWeight calculate(BigDecimal questionReliability, boolean hasQuestion, boolean hasTasteTest) {
        if (hasQuestion && hasTasteTest) {
            BigDecimal questionWeight = BOTH_RATIO.multiply(questionReliability);

            BigDecimal tasteTestWeight = TASTE_TEST_WEIGHT.subtract(questionWeight);

            return new TasteSourceDTO.PreferenceWeight(questionWeight, tasteTestWeight);
        }

        if (hasQuestion) {
            return new TasteSourceDTO.PreferenceWeight(questionReliability, BigDecimal.ZERO);
        }

        if (hasTasteTest) {
            return new TasteSourceDTO.PreferenceWeight(BigDecimal.ZERO, TASTE_TEST_WEIGHT);
        }

        return new TasteSourceDTO.PreferenceWeight(BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
