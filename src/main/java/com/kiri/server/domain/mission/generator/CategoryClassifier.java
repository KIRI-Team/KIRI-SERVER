package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.IndoorOutdoor;
import com.kiri.server.domain.place.enums.PlaceType;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class CategoryClassifier {

    private static final String TYPE_SHOPPING = "38";
    private static final Set<String> IGNORED_TYPES = Set.of("25", "32");
    private static final Set<String> IGNORED_LCLS2 = Set.of("FD04", "VE08", "VE11");
    private static final Set<String> IGNORED_LCLS3 = Set.of("VE120200");
    private static final String MARKET_LCLS2 = "SH06";

    private static final String PERFORMANCE_LCLS2 = "EV02";
    private static final String EXHIBITION_EVENT_LCLS3 = "EV030100";
    private static final Pattern PERFORMANCE_TITLE = Pattern.compile("공연|콘서트|뮤지컬|연극|음악회|오페라|발레");

    public static boolean isIgnored(String contentTypeId, String lclsSystm2, String lclsSystm3) {
        if (contentTypeId == null) {
            return false;
        }
        if (IGNORED_TYPES.contains(contentTypeId)) {
            return true;
        }
        if (TYPE_SHOPPING.equals(contentTypeId) && !MARKET_LCLS2.equals(lclsSystm2)) {
            return true;
        }
        return (lclsSystm2 != null && IGNORED_LCLS2.contains(lclsSystm2))
                || (lclsSystm3 != null && IGNORED_LCLS3.contains(lclsSystm3));
    }

    public static Optional<Classification> classify(NormalizedContent content) {
        String type = content.contentTypeId();
        if (type == null) {
            return Optional.empty();
        }
        return switch (type) {
            case "14" -> Optional.of(classifyCultureFacility(content.lclsSystm2()));
            case "15" -> Optional.of(new Classification(classifyEvent(content), PlaceType.EVENT_VENUE, null));
            case "28" -> Optional.of(new Classification(Category.EXPERIENCE, PlaceType.LEISURE, null));
            case "38" -> Optional.of(new Classification(Category.FOOD_CAFE, PlaceType.TOURIST_SPOT, IndoorOutdoor.BOTH));
            case "39" -> Optional.of(new Classification(Category.FOOD_CAFE, PlaceType.RESTAURANT, IndoorOutdoor.INDOOR));
            case "12" -> classifyTouristSpot(content.lclsSystm1(), content.lclsSystm2());
            default -> Optional.empty();
        };
    }

    private static Classification classifyCultureFacility(String middle) {
        Category category = "VE06".equals(middle) ? Category.PERFORMANCE
                : "VE09".equals(middle) ? Category.EXPERIENCE
                : Category.EXHIBITION;
        return new Classification(category, PlaceType.CULTURE_FACILITY, IndoorOutdoor.INDOOR);
    }

    private static Category classifyEvent(NormalizedContent content) {
        if (EXHIBITION_EVENT_LCLS3.equals(content.lclsSystm3())) {
            return Category.EXHIBITION;
        }
        if (PERFORMANCE_LCLS2.equals(content.lclsSystm2())
                || (content.title() != null && PERFORMANCE_TITLE.matcher(content.title()).find())) {
            return Category.PERFORMANCE;
        }
        return Category.POPUP;
    }

    private static Optional<Classification> classifyTouristSpot(String major, String middle) {
        if (major == null) {
            return Optional.empty();
        }
        return switch (major) {
            case "NA" -> spot(Category.OUTDOOR, IndoorOutdoor.OUTDOOR);
            case "HS" -> "HS04".equals(middle)
                    ? spot(Category.EXHIBITION, IndoorOutdoor.BOTH)
                    : spot(Category.OUTDOOR, IndoorOutdoor.BOTH);
            case "EX", "LS" -> spot(Category.EXPERIENCE, null);
            case "VE" -> classifyVenue(middle);
            default -> Optional.empty();
        };
    }

    private static Optional<Classification> classifyVenue(String middle) {
        if (middle == null) {
            return Optional.empty();
        }
        return switch (middle) {
            case "VE01", "VE05" -> spot(Category.OUTDOOR, IndoorOutdoor.BOTH);
            case "VE03", "VE04" -> spot(Category.OUTDOOR, IndoorOutdoor.OUTDOOR);
            case "VE02", "VE10" -> spot(Category.EXPERIENCE, null);
            case "VE06" -> spot(Category.PERFORMANCE, IndoorOutdoor.INDOOR);
            case "VE07" -> spot(Category.EXHIBITION, IndoorOutdoor.INDOOR);
            case "VE09", "VE12" -> spot(Category.EXPERIENCE, IndoorOutdoor.INDOOR);
            default -> Optional.empty();
        };
    }

    private static Optional<Classification> spot(Category category, IndoorOutdoor indoorOutdoor) {
        return Optional.of(new Classification(category, PlaceType.TOURIST_SPOT, indoorOutdoor));
    }

    public record Classification(Category category, PlaceType placeType, IndoorOutdoor indoorOutdoor) {
    }
}
