package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.place.enums.PlaceType;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class CategoryClassifier {

    private static final Set<String> IGNORED_TYPES = Set.of("25", "32", "38");

    private static final Set<String> PERFORMANCE_LCLS2 = Set.of("EV02");
    private static final Pattern PERFORMANCE_TITLE = Pattern.compile("공연|콘서트|뮤지컬|연극|음악회|오페라|발레");

    private static final Set<String> OUTDOOR_VE = Set.of("VE03");
    private static final Set<String> EXPERIENCE_VE = Set.of("VE02", "VE05");
    private static final Set<String> CULTURE_VE = Set.of("VE06", "VE07", "VE09");

    public static boolean isIgnoredType(String contentTypeId) {
        return contentTypeId != null && IGNORED_TYPES.contains(contentTypeId);
    }

    public static Optional<Classification> classify(NormalizedContent content) {
        String type = content.contentTypeId();
        if (type == null) {
            return Optional.empty();
        }
        return switch (type) {
            case "14" -> Optional.of(new Classification(Category.EXHIBITION, PlaceType.CULTURE_FACILITY, IndoorOutdoor.INDOOR));
            case "15" -> Optional.of(new Classification(
                    isPerformance(content) ? Category.PERFORMANCE : Category.POPUP, PlaceType.EVENT_VENUE, null));
            case "28" -> Optional.of(new Classification(Category.EXPERIENCE, PlaceType.LEISURE, null));
            case "39" -> Optional.of(new Classification(Category.FOOD_CAFE, PlaceType.RESTAURANT, IndoorOutdoor.INDOOR));
            case "12" -> classifyTouristSpot(content);
            default -> Optional.empty();
        };
    }

    private static boolean isPerformance(NormalizedContent content) {
        if (content.lclsSystm2() != null && PERFORMANCE_LCLS2.contains(content.lclsSystm2())) {
            return true;
        }
        return content.title() != null && PERFORMANCE_TITLE.matcher(content.title()).find();
    }

    private static Optional<Classification> classifyTouristSpot(NormalizedContent content) {
        String major = content.lclsSystm1();
        String middle = content.lclsSystm2();
        if (major == null) {
            return Optional.empty();
        }
        return switch (major) {
            case "NA" -> spot(Category.OUTDOOR, IndoorOutdoor.OUTDOOR);
            case "HS" -> spot(Category.EXHIBITION, IndoorOutdoor.BOTH);
            case "EX", "LS" -> spot(Category.EXPERIENCE, null);
            case "VE" -> classifyVenue(middle);
            default -> Optional.empty();
        };
    }

    private static Optional<Classification> classifyVenue(String middle) {
        if (middle == null) {
            return Optional.empty();
        }
        if (OUTDOOR_VE.contains(middle)) {
            return spot(Category.OUTDOOR, IndoorOutdoor.OUTDOOR);
        }
        if (EXPERIENCE_VE.contains(middle)) {
            return spot(Category.EXPERIENCE, null);
        }
        if (CULTURE_VE.contains(middle)) {
            return spot(Category.EXHIBITION, IndoorOutdoor.BOTH);
        }
        return Optional.empty();
    }

    private static Optional<Classification> spot(Category category, IndoorOutdoor indoorOutdoor) {
        return Optional.of(new Classification(category, PlaceType.TOURIST_SPOT, indoorOutdoor));
    }

    public record Classification(Category category, PlaceType placeType, IndoorOutdoor indoorOutdoor) {
    }
}
