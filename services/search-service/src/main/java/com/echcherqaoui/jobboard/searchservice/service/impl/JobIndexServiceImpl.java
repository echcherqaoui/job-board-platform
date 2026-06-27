package com.echcherqaoui.jobboard.searchservice.service.impl;

import co.elastic.clients.elasticsearch.core.UpdateResponse;
import com.echcherqaoui.jobboard.job.event.JobUpsertedEvent;
import com.echcherqaoui.jobboard.searchservice.document.JobDocument;
import com.echcherqaoui.jobboard.searchservice.exception.domain.JobDocumentNotFoundException;
import com.echcherqaoui.jobboard.searchservice.mapper.JobMapper;
import com.echcherqaoui.jobboard.searchservice.repository.JobDocumentRepository;
import com.echcherqaoui.jobboard.searchservice.service.JobIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

import static co.elastic.clients.elasticsearch._types.Result.NotFound;

@Service
@RequiredArgsConstructor
public class JobIndexServiceImpl implements JobIndexService {
    private final JobDocumentRepository jobDocumentRepository;
    private final JobMapper jobMapper;
    private final ElasticsearchTemplate elasticsearchTemplate;

    @Override
    public void upsertJob(JobUpsertedEvent event) {
        jobDocumentRepository.save(jobMapper.toDocument(event));
    }

    @Override
    public void deleteJob(String jobId) {
        jobDocumentRepository.deleteById(jobId);
    }

    @Override
    public void updateJobStatus(String jobId, String status) {
        UpdateResponse<JobDocument> response = elasticsearchTemplate.execute(client ->
              client.update(update -> update
                          .index("jobs")
                          .id(jobId)
                          .doc(Map.of("status", status)),
                    JobDocument.class
              )
        );

        if (response.result() == NotFound)
            throw new JobDocumentNotFoundException(jobId);
    }
}
