package com.kiri.server.domain.place.repository;

import com.kiri.server.domain.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findAllByName(String name);

    @Query("""
            SELECT p FROM Place p
             WHERE p.latitude BETWEEN :minLatitude AND :maxLatitude
               AND p.longitude BETWEEN :minLongitude AND :maxLongitude
            """)
    List<Place> findAllWithin(@Param("minLatitude") BigDecimal minLatitude,
                              @Param("maxLatitude") BigDecimal maxLatitude,
                              @Param("minLongitude") BigDecimal minLongitude,
                              @Param("maxLongitude") BigDecimal maxLongitude);
}
