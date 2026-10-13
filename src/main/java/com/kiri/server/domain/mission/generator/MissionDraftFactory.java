package com.kiri.server.domain.mission.generator;

import com.kiri.server.domain.external.converter.SourceTextParser;
import com.kiri.server.domain.external.converter.SourceTextParser.TimeRange;
import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.external.enums.ReviewReason;
import com.kiri.server.domain.mission.enums.Category;
import com.kiri.server.domain.mission.enums.MissionType;
import com.kiri.server.domain.mission.generator.ActivityEstimator.Estimate;
import com.kiri.server.domain.mission.generator.CategoryClassifier.Classification;
import com.kiri.server.domain.mission.generator.dto.DraftResult;
import com.kiri.server.domain.mission.generator.dto.MissionDraft;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Component
public class MissionDraftFactory {

    private static final String TYPE_EVENT = "15";
    private static final int MAX_NAME_LENGTH = 100;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.M.d");

    private static final Map<Category, String> CATEGORY_LABELS = Map.of(
            Category.EXHIBITION, "전시·문화",
            Category.POPUP, "팝업·행사",
            Category.PERFORMANCE, "공연",
            Category.FOOD_CAFE, "맛집·카페",
            Category.OUTDOOR, "산책·야외",
            Category.EXPERIENCE, "체험·놀거리");

    private final TitlePolisher titlePolisher;

    public MissionDraftFactory(TitlePolisher titlePolisher) {
        this.titlePolisher = titlePolisher;
    }

    public DraftResult create(NormalizedContent content) {
        if (CategoryClassifier.isIgnored(content.contentTypeId(), content.lclsSystm2(), content.lclsSystm3())) {
            return new DraftResult.Ignored();
        }
        if (isBlank(content.title())) {
            return new DraftResult.ReviewRequired(ReviewReason.MISSING_REQUIRED);
        }
        if (!content.hasLocation()) {
            return new DraftResult.ReviewRequired(ReviewReason.NO_LOCATION);
        }
        Optional<Classification> classification = CategoryClassifier.classify(content);
        if (classification.isEmpty()) {
            return new DraftResult.ReviewRequired(ReviewReason.CATEGORY_UNKNOWN);
        }

        boolean event = TYPE_EVENT.equals(content.contentTypeId());
        boolean hasPeriod = content.startDate() != null && content.endDate() != null
                && !content.endDate().isBefore(content.startDate());
        if (event && !hasPeriod) {
            return new DraftResult.ReviewRequired(ReviewReason.MISSING_REQUIRED);
        }

        return new DraftResult.Ready(toDraft(content, classification.get(), event ? MissionType.PERIOD : MissionType.PERMANENT));
    }

    private MissionDraft toDraft(NormalizedContent content, Classification classification, MissionType missionType) {
        Category category = classification.category();
        Estimate estimate = ActivityEstimator.estimate(category, content.durationText());
        TimeRange hours = SourceTextParser.parseTimeRange(content.usageTimeText());
        boolean period = missionType == MissionType.PERIOD;

        String title = titlePolisher.polish(MissionTitleGenerator.generate(category, content.title()), content);

        MissionDraft.PlaceDraft place = MissionDraft.PlaceDraft.builder()
                .name(limit(content.placeName(), MAX_NAME_LENGTH))
                .placeType(classification.placeType())
                .regionCode(content.regionCode())
                .latitude(content.latitude())
                .longitude(content.longitude())
                .address(content.address())
                .openingTime(period || hours == null ? null : hours.open())
                .closingTime(period || hours == null ? null : hours.close())
                .imageUrl(period ? null : content.imageUrl())
                .imageSource(period ? null : imageSource(content))
                .build();

        return MissionDraft.builder()
                .title(title)
                .description(describe(content, category, missionType))
                .category(category)
                .missionType(missionType)
                .indoorOutdoor(classification.indoorOutdoor())
                .startDate(period ? content.startDate() : null)
                .endDate(period ? content.endDate() : null)
                .openingTime(period && hours != null ? hours.open() : null)
                .closingTime(period && hours != null ? hours.close() : null)
                .price(SourceTextParser.parseFee(content.feeText()))
                .durationMinMinutes(estimate.durationMinMinutes())
                .durationMaxMinutes(estimate.durationMaxMinutes())
                .estimatedStepsMin(estimate.stepsMin())
                .estimatedStepsMax(estimate.stepsMax())
                .imageUrl(content.imageUrl())
                .imageSource(imageSource(content))
                .place(place)
                .checklist(ChecklistPlanner.plan(category, content))
                .build();
    }

    private static String describe(NormalizedContent content, Category category, MissionType missionType) {
        if (!isBlank(content.overview())) {
            return content.overview();
        }
        if (missionType == MissionType.PERIOD) {
            return period(content.startDate(), content.endDate()) + " " + eventLocation(content) + "에서 열려요.";
        }
        return content.placeName() + "에서 즐기는 " + CATEGORY_LABELS.get(category) + " 활동이에요.";
    }

    private static String period(LocalDate start, LocalDate end) {
        return start.equals(end)
                ? start.format(DATE_FORMAT)
                : start.format(DATE_FORMAT) + " ~ " + end.format(DATE_FORMAT);
    }

    private static String eventLocation(NormalizedContent content) {
        return isBlank(content.venueName()) ? content.address() : content.venueName();
    }

    private static String imageSource(NormalizedContent content) {
        return content.imageUrl() == null ? null : content.imageSource();
    }

    private static String limit(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max).trim();
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
