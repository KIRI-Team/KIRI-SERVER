package com.kiri.server.admin.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record AdminPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <E, T> AdminPageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new AdminPageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static <T> AdminPageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new AdminPageResponse<>(content, page, size, totalElements, totalPages);
    }
}
