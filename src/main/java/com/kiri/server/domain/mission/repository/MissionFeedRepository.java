package com.kiri.server.domain.mission.repository;

import com.kiri.server.domain.mission.dto.MissionReqDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class MissionFeedRepository {

    private static final double KM_PER_DEGREE = 111.0;

    private static final String OPENING = "CASE WHEN m.opening_time IS NULL AND m.closing_time IS NULL"
            + " THEN p.opening_time ELSE m.opening_time END";
    private static final String CLOSING = "CASE WHEN m.opening_time IS NULL AND m.closing_time IS NULL"
            + " THEN p.closing_time ELSE m.closing_time END";

    private final EntityManager entityManager;

    public record BaseLocation(double latitude, double longitude) {
    }

    public List<Long> findFeedIds(MissionReqDTO.FeedCondition condition, BaseLocation base,
                                  LocalDate today, int limit) {
        return findFeedIds(condition, base, today, limit, null, null);
    }

    // includeOnly: 이 미션들 중에서만 고른다(끼리 픽 상단). exclude: 이 미션들은 뺀다(상단에 이미 나온 픽).
    public List<Long> findFeedIds(MissionReqDTO.FeedCondition condition, BaseLocation base, LocalDate today, int limit,
                                  Collection<Long> includeOnly, Collection<Long> exclude) {
        StringBuilder sql = new StringBuilder("""
                SELECT m.mission_id
                  FROM mission m
                  JOIN place p ON p.place_id = m.place_id
                 WHERE m.status = 'ACTIVE'
                   AND (m.end_date IS NULL OR m.end_date >= :today)
                """);
        Map<String, Object> params = new HashMap<>();
        params.put("today", today);
        if (includeOnly != null) {
            if (includeOnly.isEmpty()) {
                return List.of();
            }
            sql.append(" AND m.mission_id IN (:includeOnly)");
            params.put("includeOnly", includeOnly);
        }
        if (exclude != null && !exclude.isEmpty()) {
            sql.append(" AND m.mission_id NOT IN (:exclude)");
            params.put("exclude", exclude);
        }

        if (condition.category() != null) {
            sql.append(" AND m.category = :category");
            params.put("category", condition.category().name());
        }
        if (condition.regionCode() != null) {
            sql.append(" AND p.region_code LIKE :regionCode");
            params.put("regionCode", condition.regionCode() + "%");
        }
        if (condition.distance() != null && base != null) {
            appendDistance(sql, params, base, condition.distance());
        }
        if (condition.date() != null) {
            sql.append("""
                     AND (m.mission_type = 'PERMANENT'
                          OR ((m.start_date IS NULL OR m.start_date <= :date)
                              AND (m.end_date IS NULL OR m.end_date >= :date)))
                    """);
            params.put("date", condition.date());
        }
        if (condition.time() != null) {
            sql.append(" AND (").append(OPENING).append(" IS NULL OR ").append(CLOSING).append(" IS NULL")
                    .append(" OR (").append(OPENING).append(" <= ").append(CLOSING)
                    .append(" AND :time >= ").append(OPENING).append(" AND :time < ").append(CLOSING).append(")")
                    .append(" OR (").append(OPENING).append(" > ").append(CLOSING)
                    .append(" AND (:time >= ").append(OPENING).append(" OR :time < ").append(CLOSING).append(")))");
            params.put("time", condition.time());
        }
        if (condition.freeOnly()) {
            sql.append(" AND (m.price IS NULL OR m.price = 0)");
        } else if (condition.maxPrice() != null) {
            sql.append(" AND (m.price IS NULL OR m.price <= :maxPrice)");
            params.put("maxPrice", condition.maxPrice());
        }
        if (condition.cursor() != null) {
            sql.append(" AND m.mission_id < :cursor");
            params.put("cursor", condition.cursor());
        }
        sql.append(" ORDER BY m.mission_id DESC LIMIT :limit");
        params.put("limit", limit);

        Query query = entityManager.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);

        return ((List<?>) query.getResultList()).stream()
                .map(id -> ((Number) id).longValue())
                .toList();
    }

    private void appendDistance(StringBuilder sql, Map<String, Object> params,
                                BaseLocation base, int distanceKm) {
        double latitude = base.latitude();
        double longitude = base.longitude();
        double distance = distanceKm;
        double latitudeDelta = distance / KM_PER_DEGREE;
        double longitudeDelta = distance / (KM_PER_DEGREE * Math.cos(Math.toRadians(latitude)));

        sql.append("""
                 AND p.latitude BETWEEN :minLatitude AND :maxLatitude
                 AND p.longitude BETWEEN :minLongitude AND :maxLongitude
                 AND 2 * 6371 * asin(sqrt(
                         power(sin(radians(p.latitude - :latitude) / 2), 2)
                         + cos(radians(:latitude)) * cos(radians(p.latitude))
                         * power(sin(radians(p.longitude - :longitude) / 2), 2))) <= :distance
                """);
        params.put("minLatitude", latitude - latitudeDelta);
        params.put("maxLatitude", latitude + latitudeDelta);
        params.put("minLongitude", longitude - longitudeDelta);
        params.put("maxLongitude", longitude + longitudeDelta);
        params.put("latitude", latitude);
        params.put("longitude", longitude);
        params.put("distance", distance);
    }
}
