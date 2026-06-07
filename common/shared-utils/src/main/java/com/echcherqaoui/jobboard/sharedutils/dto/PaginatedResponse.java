package com.echcherqaoui.jobboard.sharedutils.dto;

import org.springframework.data.domain.Page;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.function.Function;

public record PaginatedResponse<T>(List<T> content,
                                   int page,
                                   int size,
                                   long totalElements,
                                   int totalPages,
                                   boolean last
) {
    @NonNull
    public static <E, D> PaginatedResponse<D> of(@NonNull Page<E> page,@NonNull  Function<E, D> mapper) {
        return new PaginatedResponse<>(
              page.getContent().stream().map(mapper).toList(),
              page.getNumber(),
              page.getSize(),
              page.getTotalElements(),
              page.getTotalPages(),
              page.isLast()
        );
    }
}
