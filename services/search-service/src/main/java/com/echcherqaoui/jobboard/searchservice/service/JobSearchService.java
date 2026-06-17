package com.echcherqaoui.jobboard.searchservice.service;

import com.echcherqaoui.jobboard.searchservice.dto.request.JobSearchRequest;
import com.echcherqaoui.jobboard.searchservice.dto.response.AutocompleteResponse;
import com.echcherqaoui.jobboard.searchservice.dto.response.JobDTO;
import com.echcherqaoui.jobboard.searchservice.dto.response.JobSearchResponse;

public interface JobSearchService {
    JobSearchResponse search(JobSearchRequest request);

    JobDTO getById(String id);

    AutocompleteResponse autocomplete(String prefix, int maxSuggestions);
}
