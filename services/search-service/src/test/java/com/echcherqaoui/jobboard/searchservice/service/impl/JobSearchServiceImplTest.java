package com.echcherqaoui.jobboard.searchservice.service.impl;

import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import com.echcherqaoui.jobboard.searchservice.document.JobDocument;
import com.echcherqaoui.jobboard.searchservice.dto.request.JobSearchRequest;
import com.echcherqaoui.jobboard.searchservice.dto.response.JobDTO;
import com.echcherqaoui.jobboard.searchservice.dto.response.JobSearchResponse;
import com.echcherqaoui.jobboard.searchservice.exception.domain.JobDocumentNotFoundException;
import com.echcherqaoui.jobboard.searchservice.exception.domain.SearchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobSearchServiceImplTest {

    private ElasticsearchTemplate elasticsearchTemplate;
    private JobSearchServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        elasticsearchTemplate = mock(ElasticsearchTemplate.class);
        service = new JobSearchServiceImpl(elasticsearchTemplate);

        setField(service, "highlightPreTag", "<mark>");
        setField(service, "highlightPostTag", "</mark>");
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = JobSearchServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private JobDocument buildDocument(String id, String title) {
        return new JobDocument()
              .setId(id)
              .setRecruiterId("recruiter-1")
              .setCompanyName("Acme")
              .setTitle(title)
              .setLocation("Casablanca")
              .setWorkModality("REMOTE")
              .setJobType("FULL_TIME")
              .setExperienceLevel("MID")
              .setStatus("OPEN")
              .setSalaryMin(50000.0)
              .setSalaryMax(80000.0)
              .setCurrency("USD")
              .setSkills(List.of("Java", "Spring"))
              .setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @SuppressWarnings("unchecked")
    private Hit<JobDocument> buildHit(JobDocument doc, Double score) {
        Hit<JobDocument> hit = mock(Hit.class);
        when(hit.source()).thenReturn(doc);
        when(hit.score()).thenReturn(score);
        when(hit.highlight()).thenReturn(Map.of());
        return hit;
    }

    @SuppressWarnings("unchecked")
    private SearchResponse<JobDocument> buildSearchResponse(List<Hit<JobDocument>> hits, Long totalValue, long tookMs) {
        SearchResponse<JobDocument> response = mock(SearchResponse.class);
        HitsMetadata<JobDocument> hitsMetadata = mock(HitsMetadata.class);

        when(response.hits()).thenReturn(hitsMetadata);
        when(hitsMetadata.hits()).thenReturn(hits);
        when(response.took()).thenReturn(tookMs);

        if (totalValue != null) {
            TotalHits totalHits = mock(TotalHits.class);
            when(totalHits.value()).thenReturn(totalValue);
            when(hitsMetadata.total()).thenReturn(totalHits);
        } else {
            when(hitsMetadata.total()).thenReturn(null);
        }

        return response;
    }

    private JobSearchRequest buildRequest(int page, int size) {
        return new JobSearchRequest(
              "java developer", null, null, null, null, null, null,
              null, null, null, page, size, "relevance", null
        );
    }

    // ---------- search ----------

    @Test
    void search_mapsHitsToJobSearchHits_preservingAllFields() {
        JobDocument doc = buildDocument("job-1", "Backend Engineer");
        Hit<JobDocument> hit = buildHit(doc, 4.5);
        SearchResponse<JobDocument> response = buildSearchResponse(List.of(hit), 1L, 12L);

        when(elasticsearchTemplate.execute(any())).thenReturn(response);

        JobSearchResponse result = service.search(buildRequest(0, 20));

        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).id()).isEqualTo("job-1");
        assertThat(result.hits().get(0).title()).isEqualTo("Backend Engineer");
        assertThat(result.hits().get(0).score()).isEqualTo(4.5f);
        assertThat(result.totalHits()).isEqualTo(1);
        assertThat(result.tookMs()).isEqualTo(12L);
    }

    @Test
    void search_defaultsScoreToZero_whenHitScoreNull() {
        JobDocument doc = buildDocument("job-1", "Backend Engineer");
        Hit<JobDocument> hit = buildHit(doc, null);
        SearchResponse<JobDocument> response = buildSearchResponse(List.of(hit), 1L, 5L);

        when(elasticsearchTemplate.execute(any())).thenReturn(response);

        JobSearchResponse result = service.search(buildRequest(0, 20));

        assertThat(result.hits().get(0).score()).isEqualTo(0f);
    }

    @Test
    void search_throwsSearchException_whenHitSourceIsNull() {
        Hit<JobDocument> hit = buildHit(null, 1.0);
        SearchResponse<JobDocument> response = buildSearchResponse(List.of(hit), 1L, 5L);

        when(elasticsearchTemplate.execute(any())).thenReturn(response);

        assertThatThrownBy(() -> service.search(buildRequest(0, 20)))
              .isInstanceOf(SearchException.class);
    }

    @Test
    void search_computesTotalPagesAndHasNext_correctly() {
        SearchResponse<JobDocument> response = buildSearchResponse(List.of(), 45L, 3L);
        when(elasticsearchTemplate.execute(any())).thenReturn(response);

        // page=0, size=20 -> 45 total -> 3 pages (ceil(45/20)=3), hasNext = (0+1) < 3 = true
        JobSearchResponse result = service.search(buildRequest(0, 20));

        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void search_hasNextFalse_onLastPage() {
        SearchResponse<JobDocument> response = buildSearchResponse(List.of(), 45L, 3L);
        when(elasticsearchTemplate.execute(any())).thenReturn(response);

        // page=2, size=20 -> 3 pages -> hasNext = (2+1) < 3 = false
        JobSearchResponse result = service.search(buildRequest(2, 20));

        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void search_returnsZeroTotalHits_whenTotalHitsMetadataNull() {
        SearchResponse<JobDocument> response = buildSearchResponse(List.of(), null, 1L);
        when(elasticsearchTemplate.execute(any())).thenReturn(response);

        JobSearchResponse result = service.search(buildRequest(0, 20));

        assertThat(result.totalHits()).isZero();
    }

    // ---------- getById ----------

    @Test
    void getById_returnsJobDTO_whenFound() {
        JobDocument doc = buildDocument("job-1", "Backend Engineer");
        GetResponse<JobDocument> response = mock(GetResponse.class);
        when(response.found()).thenReturn(true);
        when(response.source()).thenReturn(doc);

        when(elasticsearchTemplate.execute(any())).thenReturn(response);

        JobDTO result = service.getById("job-1");

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("job-1");
        assertThat(result.title()).isEqualTo("Backend Engineer");
    }

    @Test
    void getById_throwsJobDocumentNotFoundException_whenNotFound() {
        GetResponse<JobDocument> response = mock(GetResponse.class);
        when(response.found()).thenReturn(false);

        when(elasticsearchTemplate.execute(any())).thenReturn(response);

        assertThatThrownBy(() -> service.getById("missing-id"))
              .isInstanceOf(JobDocumentNotFoundException.class);
    }

    @Test
    void getById_throwsJobDocumentNotFoundException_whenFoundButSourceNull() {
        GetResponse<JobDocument> response = mock(GetResponse.class);
        when(response.found()).thenReturn(true);
        when(response.source()).thenReturn(null);

        when(elasticsearchTemplate.execute(any())).thenReturn(response);

        assertThatThrownBy(() -> service.getById("job-1"))
              .isInstanceOf(JobDocumentNotFoundException.class);
    }

    // ---------- autocomplete ----------

    @Test
    void autocomplete_returnsEmpty_whenPrefixNullOrBlank() {
        assertThat(service.autocomplete(null, 5).suggestions()).isEmpty();
        assertThat(service.autocomplete("  ", 5).suggestions()).isEmpty();
    }

    @Test
    void autocomplete_returnsDistinctTitles_filteringNullSources() {
        JobDocument doc1 = buildDocument("job-1", "Backend Engineer");
        JobDocument doc2 = buildDocument("job-2", "Backend Engineer"); // duplicate title
        JobDocument doc3 = buildDocument("job-3", "Backend Lead");

        Hit<JobDocument> hit1 = buildHit(doc1, 1.0);
        Hit<JobDocument> hit2 = buildHit(doc2, 1.0);
        Hit<JobDocument> hit3 = buildHit(doc3, 1.0);
        Hit<JobDocument> nullSourceHit = buildHit(null, 1.0);

        SearchResponse<JobDocument> response = buildSearchResponse(
              List.of(hit1, hit2, hit3, nullSourceHit), 4L, 2L
        );
        when(elasticsearchTemplate.execute(any())).thenReturn(response);

        var result = service.autocomplete("back", 10);

        assertThat(result.suggestions()).containsExactlyInAnyOrder("Backend Engineer", "Backend Lead");
    }
}