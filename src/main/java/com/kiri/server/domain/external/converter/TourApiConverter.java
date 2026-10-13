package com.kiri.server.domain.external.converter;

import com.kiri.server.domain.external.client.tourapi.dto.TourApiResDTO;
import com.kiri.server.domain.external.dto.NormalizedContent;
import com.kiri.server.domain.external.enums.ExternalSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.kiri.server.domain.external.client.tourapi.TourApiResponseParser.text;

public class TourApiConverter {

    private static final DateTimeFormatter MODIFIED_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String HIDDEN = "0";
    private static final String IMAGE_SOURCE = "한국관광공사";
    private static final int MAX_ADDRESS_LENGTH = 255;
    private static final int MAX_URL_LENGTH = 500;
    private static final Pattern HREF = Pattern.compile("href\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAIN_URL = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);

    private static final String TYPE_EVENT = "15";
    private static final String TYPE_RESTAURANT = "39";

    private static final Map<String, String> USAGE_TIME_FIELDS = Map.of(
            "12", "usetime", "14", "usetimeculture", "15", "playtime", "28", "usetimeleports", "39", "opentimefood");
    private static final Map<String, String> FEE_FIELDS = Map.of(
            "14", "usefee", "15", "usetimefestival", "28", "usefeeleports");
    private static final Map<String, String> DURATION_FIELDS = Map.of(
            "14", "spendtime", "15", "spendtimefestival");
    private static final Map<String, List<String>> PROGRAM_FIELDS = Map.of(
            "12", List.of("expguide"), "15", List.of("program", "subevent"));

    public static TourApiResDTO.SyncItem toSyncItem(JsonNode item) {
        return TourApiResDTO.SyncItem.builder()
                .contentId(text(item, "contentid"))
                .contentTypeId(text(item, "contenttypeid"))
                .title(text(item, "title"))
                .address(text(item, "addr1"))
                .addressDetail(text(item, "addr2"))
                .longitude(decimal(text(item, "mapx")))
                .latitude(decimal(text(item, "mapy")))
                .imageUrl(text(item, "firstimage"))
                .copyrightType(text(item, "cpyrhtDivCd"))
                .regionCode(text(item, "lDongRegnCd"))
                .sigunguCode(text(item, "lDongSignguCd"))
                .lclsSystm1(text(item, "lclsSystm1"))
                .lclsSystm2(text(item, "lclsSystm2"))
                .lclsSystm3(text(item, "lclsSystm3"))
                .shown(!HIDDEN.equals(text(item, "showflag")))
                .modifiedAt(dateTime(text(item, "modifiedtime")))
                .raw(item)
                .build();
    }

    public static NormalizedContent toNormalizedContent(TourApiResDTO.SyncItem item, JsonNode common,
                                                        JsonNode intro, List<JsonNode> infos) {
        JsonNode commonNode = orMissing(common);
        JsonNode introNode = orMissing(intro);
        List<JsonNode> infoNodes = infos == null ? List.of() : infos;
        String type = item.contentTypeId();

        return NormalizedContent.builder()
                .source(ExternalSource.TOUR_API)
                .externalId(item.contentId())
                .contentTypeId(type)
                .title(firstNonNull(clean(commonNode, "title"), item.title()))
                .overview(clean(commonNode, "overview"))
                .address(joinAddress(item.address(), item.addressDetail()))
                .latitude(item.latitude() != null ? item.latitude() : decimal(text(commonNode, "mapy")))
                .longitude(item.longitude() != null ? item.longitude() : decimal(text(commonNode, "mapx")))
                .regionCode(joinRegionCode(item.regionCode(), item.sigunguCode()))
                .imageUrl(firstNonNull(item.imageUrl(), text(commonNode, "firstimage")))
                .imageSource(IMAGE_SOURCE)
                .lclsSystm1(firstNonNull(item.lclsSystm1(), text(commonNode, "lclsSystm1")))
                .lclsSystm2(firstNonNull(item.lclsSystm2(), text(commonNode, "lclsSystm2")))
                .lclsSystm3(firstNonNull(item.lclsSystm3(), text(commonNode, "lclsSystm3")))
                .startDate(TYPE_EVENT.equals(type) ? SourceTextParser.parseCompactDate(text(introNode, "eventstartdate")) : null)
                .endDate(TYPE_EVENT.equals(type) ? SourceTextParser.parseCompactDate(text(introNode, "eventenddate")) : null)
                .venueName(TYPE_EVENT.equals(type) ? clean(introNode, "eventplace") : null)
                .usageTimeText(clean(introNode, fieldFor(USAGE_TIME_FIELDS, type)))
                .feeText(clean(introNode, fieldFor(FEE_FIELDS, type)))
                .durationText(clean(introNode, fieldFor(DURATION_FIELDS, type)))
                .programTexts(programTexts(type, introNode, infoNodes))
                .menuNames(menuNames(type, introNode))
                .build();
    }

    public static String toSourceUrl(JsonNode common) {
        String homepage = text(orMissing(common), "homepage");
        if (homepage == null) {
            return null;
        }
        Matcher href = HREF.matcher(homepage);
        Matcher plain = PLAIN_URL.matcher(homepage);
        String url = href.find() ? href.group(1).trim() : plain.find() ? plain.group() : null;
        return url == null || url.length() > MAX_URL_LENGTH ? null : url;
    }

    private static List<String> programTexts(String type, JsonNode intro, List<JsonNode> infos) {
        List<String> texts = new ArrayList<>();
        for (String field : type == null ? List.<String>of() : PROGRAM_FIELDS.getOrDefault(type, List.of())) {
            addIfPresent(texts, clean(intro, field));
        }
        for (JsonNode info : infos) {
            String name = text(info, "infoname");
            if (name != null && name.contains("프로그램")) {
                addIfPresent(texts, clean(info, "infotext"));
            }
        }
        return texts;
    }

    private static List<String> menuNames(String type, JsonNode intro) {
        if (!TYPE_RESTAURANT.equals(type)) {
            return List.of();
        }
        List<String> menus = new ArrayList<>();
        menus.addAll(SourceTextParser.splitItems(clean(intro, "firstmenu")));
        menus.addAll(SourceTextParser.splitItems(clean(intro, "treatmenu")));
        return menus.stream().distinct().toList();
    }

    private static String fieldFor(Map<String, String> fields, String type) {
        return type == null ? null : fields.get(type);
    }

    private static String clean(JsonNode node, String field) {
        return field == null ? null : SourceTextParser.stripHtml(text(node, field));
    }

    private static String joinAddress(String address, String detail) {
        if (address == null) {
            return null;
        }
        String joined = detail == null ? address : address + " " + detail;
        return joined.length() <= MAX_ADDRESS_LENGTH ? joined : address;
    }

    private static String joinRegionCode(String regionCode, String sigunguCode) {
        if (regionCode == null) {
            return null;
        }
        return sigunguCode == null ? regionCode : regionCode + sigunguCode;
    }

    private static void addIfPresent(List<String> list, String value) {
        if (value != null) {
            list.add(value);
        }
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    private static JsonNode orMissing(JsonNode node) {
        return node == null ? MissingNode.getInstance() : node;
    }

    private static BigDecimal decimal(String value) {
        if (value == null) {
            return null;
        }
        try {
            BigDecimal decimal = new BigDecimal(value);
            return decimal.signum() == 0 ? null : decimal;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime dateTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, MODIFIED_TIME_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
