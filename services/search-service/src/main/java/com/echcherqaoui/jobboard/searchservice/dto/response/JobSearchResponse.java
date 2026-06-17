package com.echcherqaoui.jobboard.searchservice.dto.response;

import java.util.List;

public record JobSearchResponse(
        List<JobSearchHit> hits,
        long totalHits,
        int page,
        int size,
        int totalPages,
        boolean hasNext,
        long tookMs           // Elasticsearch query time in milliseconds
) {}
