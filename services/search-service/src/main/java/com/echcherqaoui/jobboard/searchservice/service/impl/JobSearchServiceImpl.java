package com.echcherqaoui.jobboard.searchservice.service.impl;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import com.echcherqaoui.jobboard.searchservice.document.JobDocument;
import com.echcherqaoui.jobboard.searchservice.dto.request.JobSearchRequest;
import com.echcherqaoui.jobboard.searchservice.dto.response.AutocompleteResponse;
import com.echcherqaoui.jobboard.searchservice.dto.response.JobDTO;
import com.echcherqaoui.jobboard.searchservice.dto.response.JobSearchHit;
import com.echcherqaoui.jobboard.searchservice.dto.response.JobSearchResponse;
import com.echcherqaoui.jobboard.searchservice.exception.domain.JobDocumentNotFoundException;
import com.echcherqaoui.jobboard.searchservice.exception.domain.SearchException;
import com.echcherqaoui.jobboard.searchservice.service.JobSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static co.elastic.clients.elasticsearch._types.SortOrder.Asc;
import static co.elastic.clients.elasticsearch._types.SortOrder.Desc;
import static co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields;
import static com.echcherqaoui.jobboard.searchservice.exception.enums.SearchErrorCode.NULL_SOURCE;
import static org.apache.logging.log4j.util.Strings.isNotBlank;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobSearchServiceImpl implements JobSearchService {

    private final ElasticsearchTemplate elasticsearchTemplate;

    @Value("${search.highlight.pre-tag:<mark>}")
    private String highlightPreTag;

    @Value("${search.highlight.post-tag:</mark>}")
    private String highlightPostTag;

    /**
     * Filters jobs that are either permanent (no expiry date) or still active.
     */
    private void applyExpiryFilter(@NonNull BoolQuery.Builder builder) {
        builder.filter(filter -> filter
              .bool(fBool -> fBool
                    .should(should -> should
                          .bool(sBool -> sBool
                                .mustNot(mustNot -> mustNot
                                      .exists(exists -> exists.field("expiresAt"))
                                )
                          )
                    ).should(should -> should
                          .range(range -> range
                                .date(date -> date
                                      .field("expiresAt")
                                      .gt(Instant.now().toString())
                                )
                          )
                    ).minimumShouldMatch("1")
              ));
    }

    /**
     * Filters jobs by exact publication status (e.g., OPEN).
     */
    private void applyStatusFilter(@NonNull BoolQuery.Builder builder, String status) {
        if (status == null || status.isBlank())
            return;

        builder.filter(filter -> filter
              .term(term -> term
                    .field("status")
                    .value(status)
              )
        );
    }

    /**
     * Executes a multi-field, fuzzy search using user-provided keywords.
     * Applies relevancy boosting on critical fields like title and skills.
     */
    private void applyKeywordQuery(@NonNull BoolQuery.Builder builder, String keyword) {
        if (keyword == null || keyword.isBlank())
            return;

        builder.must(must -> must
              .multiMatch(multiMatch -> multiMatch
                    .query(keyword)
                    .fields("title^4", "title.autocomplete^2", "description", "requirements", "skills^3")
                    .type(BestFields)
                    .fuzziness("AUTO")
                    .minimumShouldMatch("75%")
              )
        );
    }

    /**
     * Filters results by company name.
     */
    private void applyCompanyFilter(@NonNull BoolQuery.Builder builder, String companyName) {
        if (companyName == null || companyName.isBlank())
            return;

        builder.filter(filter -> filter
              .term(term -> term
                    .field("companyName")
                    .value(companyName)
              )
        );
    }

    /**
     * Filters results by specific work location.
     */
    private void applyLocationFilter(@NonNull BoolQuery.Builder builder, String location) {
        if (location == null || location.isBlank()) return;

        builder.filter(filter -> filter
              .match(match -> match
                    .field("location")
                    .query(location)
              ));
    }

    /**
     * Filters results by work modality (e.g., Remote, Hybrid).
     */
    private void applyWorkModalityFilter(BoolQuery.Builder builder, List<String> workModalities) {
        if (!CollectionUtils.isEmpty(workModalities))
            builder.filter(filter -> filter
                  .terms(terms -> terms
                        .field("workModality")
                        .terms(termsQuery -> termsQuery.value(
                              workModalities.stream()
                                    .map(FieldValue::of)
                                    .toList()
                        ))
                  ));
    }

    /**
     * Filters results by employment type (e.g., Full-time, Contract).
     */
    private void applyJobTypeFilter(BoolQuery.Builder builder, List<String> jobTypes) {
        if (!CollectionUtils.isEmpty(jobTypes))
            builder.filter(filter -> filter
                  .terms(terms -> terms
                        .field("jobType")
                        .terms(termsQuery -> termsQuery.value(
                              jobTypes.stream()
                                    .map(FieldValue::of)
                                    .toList()
                        ))
                  ));
    }

    /**
     * Filters results by required candidate experience level.
     */
    private void applyExperienceLevelFilter(BoolQuery.Builder builder, List<String> experienceLevels) {
        if (!CollectionUtils.isEmpty(experienceLevels))
            builder.filter(filter -> filter
                  .terms(terms -> terms
                        .field("experienceLevel")
                        .terms(termsQuery -> termsQuery.value(
                              experienceLevels.stream()
                                    .map(FieldValue::of)
                                    .toList()
                        ))
                  ));
    }

    /**
     * Filters results to include jobs matching requested technical or professional skills.
     */
    private void applySkillsFilter(@NonNull BoolQuery.Builder builder, List<String> skills) {

        if (!CollectionUtils.isEmpty(skills))
            builder.filter(filter -> filter
                  .terms(terms -> terms
                        .field("skills")
                        .terms(termsQuery -> termsQuery.value(
                              skills.stream()
                                    .map(FieldValue::of)
                                    .toList()
                        ))
                  ));
    }

    /**
     * Applies inclusive numeric range limits for salary.
     */
    private void applySalaryFilter(BoolQuery.Builder builder, Double salaryMin, Double salaryMax) {
        if (salaryMin == null && salaryMax == null) return;

        builder.filter(filter -> filter
              .range(range -> range
                    .number(rangeNumber -> {
                        rangeNumber.field("salaryMin");
                        if (salaryMin != null) rangeNumber.gte(salaryMin);
                        if (salaryMax != null) rangeNumber.lte(salaryMax);

                        return rangeNumber;
                    })
              ));
    }

    /**
     * Assembles all criteria filters and keyword match queries into a single immutable Elasticsearch bool query.
     */

    private Query buildQuery(JobSearchRequest req) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        applyExpiryFilter(boolBuilder);
        applyStatusFilter(boolBuilder, req.status());
        applyKeywordQuery(boolBuilder, req.keyword());
        applyCompanyFilter(boolBuilder, req.companyName());
        applyLocationFilter(boolBuilder, req.location());
        applyWorkModalityFilter(boolBuilder, req.workModalities());
        applyJobTypeFilter(boolBuilder, req.jobTypes());
        applyExperienceLevelFilter(boolBuilder, req.experienceLevels());
        applySkillsFilter(boolBuilder, req.skills());
        applySalaryFilter(boolBuilder, req.salaryMin(), req.salaryMax());

        return Query.of(q -> q.bool(boolBuilder.build()));
    }

    private Map<String, HighlightField> buildHighlightFields() {
        return Map.of(
              "title", HighlightField.of(highlightField -> highlightField.numberOfFragments(0)),
              "description", HighlightField.of(highlightField -> highlightField.numberOfFragments(3).fragmentSize(150)),
              "requirements", HighlightField.of(highlightField -> highlightField.numberOfFragments(2).fragmentSize(150))
        );
    }

    private void applySorting(SearchRequest.Builder searchBuilder, String sortBy, String sortDirection) {
        // Return early if sorting by relevance and a keyword is present
        if ("relevance".equals(sortBy))
            return;

        SortOrder sortOrder = "asc".equalsIgnoreCase(sortDirection) ? Asc : Desc;

        String sortField = switch (sortBy) {
            case "salaryMin" -> "salaryMin";
            case "salaryMax" -> "salaryMax";
            default -> "createdAt";
        };

        searchBuilder.sort(sortOptions -> sortOptions
              .field(fieldSort -> fieldSort
                    .field(sortField)
                    .order(sortOrder)
              )
        );
    }

    private SearchRequest buildSearchRequest(JobSearchRequest searchRequest) {
        return SearchRequest.of(searchBuilder -> {
            searchBuilder.index("jobs")
                  .from(searchRequest.page() * searchRequest.size())
                  .size(searchRequest.size())
                  .query(buildQuery(searchRequest))
                  .trackTotalHits(track -> track.enabled(true));

            if (isNotBlank(searchRequest.keyword()))
                searchBuilder.highlight(h -> h
                      .fields(buildHighlightFields())
                      .preTags(highlightPreTag)
                      .postTags(highlightPostTag)
                );

            applySorting(searchBuilder, searchRequest.sortBy(), searchRequest.sortDirection());

            return searchBuilder;
        });
    }

    private JobSearchHit toSearchHit(@NonNull Hit<JobDocument> hit) {
        JobDocument doc = hit.source();
        if (doc == null)
            throw new SearchException(NULL_SOURCE);

        return new JobSearchHit(
              doc.getId(),
              doc.getRecruiterId(),
              doc.getCompanyName(),
              doc.getCompanyLogo(),
              doc.getTitle(),
              doc.getLocation(),
              doc.getWorkModality(),
              doc.getJobType(),
              doc.getExperienceLevel(),
              doc.getStatus(),
              doc.getSalaryMin(),
              doc.getSalaryMax(),
              doc.getCurrency(),
              doc.getSkills(),
              doc.getCreatedAt(),
              doc.getExpiresAt(),
              hit.score() != null ? hit.score().floatValue() : 0f,
              hit.highlight() != null ? hit.highlight() : Map.of()
        );
    }

    @NonNull
    private JobSearchResponse buildResponse(@NonNull SearchResponse<JobDocument> response,
                                            @NonNull JobSearchRequest req,
                                            long tookMs) {
        TotalHits total = response.hits().total();
        long totalHits = total != null ? total.value() : 0L;

        List<JobSearchHit> hits = response.hits().hits().stream()
              .map(this::toSearchHit)
              .toList();

        int totalPages = (int) Math.ceil((double) totalHits / req.size());

        return new JobSearchResponse(
              hits,
              totalHits,
              req.page(),
              req.size(),
              totalPages,
              (req.page() + 1) < totalPages,
              tookMs
        );
    }

    /**
     * Executes primary paginated search against the Elasticsearch jobs index.
     */
    @Override
    public JobSearchResponse search(JobSearchRequest request) {
        SearchRequest esRequest = buildSearchRequest(request);

        SearchResponse<JobDocument> response = elasticsearchTemplate
              .execute(client -> client.search(esRequest, JobDocument.class));

        return buildResponse(response, request, response.took());
    }

    @Override
    public JobDTO getById(String id) {
        GetRequest request = GetRequest.of(g -> g.index("jobs").id(id));

        GetResponse<JobDocument> response = elasticsearchTemplate
              .execute(client -> client.get(request, JobDocument.class));

        if (!response.found() || response.source() == null)
            throw new JobDocumentNotFoundException(id);

        return JobDTO.from(response.source());
    }

    /**
     * Lightweight title-only completion suggester for real-time typeahead lookups.
     */
    @Override
    public AutocompleteResponse autocomplete(String prefix, int maxSuggestions) {
        if (prefix == null || prefix.isBlank())
            return new AutocompleteResponse(List.of());

        SearchRequest esRequest = SearchRequest.of(s -> s
              .index("jobs")
              .size(maxSuggestions)
              .source(src -> src.filter(filter -> filter.includes("title")))
              .query(q -> q
                    .bool(b -> b
                          .must(must -> must.term(term -> term.field("status").value("OPEN")))
                          .must(must -> must.match(match -> match
                                .field("title.autocomplete")
                                .query(prefix)
                          ))
                    )
              )
        );

        SearchResponse<JobDocument> response = elasticsearchTemplate
              .execute(client -> client.search(esRequest, JobDocument.class));

        List<String> suggestions = response.hits().hits().stream()
              .map(Hit::source)
              .filter(Objects::nonNull)
              .map(JobDocument::getTitle)
              .distinct()
              .toList();

        return new AutocompleteResponse(suggestions);
    }
}
