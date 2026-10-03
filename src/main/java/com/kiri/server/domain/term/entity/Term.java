package com.kiri.server.domain.term.entity;

import com.kiri.server.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "term")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Term extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "term_id")
    private Long id;

    @Column(length = 50, nullable = false)
    private String title;

    @Column(name = "short_description", length = 100, nullable = false)
    private String shortDescription;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private Boolean isRequired = true;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;
}
