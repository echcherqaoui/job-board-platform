package com.echcherqaoui.jobboard.searchservice.service.impl;

import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import com.echcherqaoui.jobboard.job.event.JobUpsertedEvent;
import com.echcherqaoui.jobboard.searchservice.document.JobDocument;
import com.echcherqaoui.jobboard.searchservice.exception.domain.JobDocumentNotFoundException;
import com.echcherqaoui.jobboard.searchservice.mapper.JobMapper;
import com.echcherqaoui.jobboard.searchservice.repository.JobDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobIndexServiceImplTest {

    private JobDocumentRepository jobDocumentRepository;
    private JobMapper jobMapper;
    private ElasticsearchTemplate elasticsearchTemplate;
    private JobIndexServiceImpl service;

    @BeforeEach
    void setUp() {
        jobDocumentRepository = mock(JobDocumentRepository.class);
        jobMapper = mock(JobMapper.class);
        elasticsearchTemplate = mock(ElasticsearchTemplate.class);
        service = new JobIndexServiceImpl(jobDocumentRepository, jobMapper, elasticsearchTemplate);
    }


    @Test
    void upsertJob_mapsEventToDocument_andSaves() {
        JobUpsertedEvent event = JobUpsertedEvent.newBuilder().build();
        JobDocument mappedDoc = new JobDocument().setId("job-1");

        when(jobMapper.toDocument(event)).thenReturn(mappedDoc);

        service.upsertJob(event);

        verify(jobDocumentRepository).save(mappedDoc);
    }

    @Test
    void deleteJob_delegatesToRepository() {
        service.deleteJob("job-1");

        verify(jobDocumentRepository).deleteById("job-1");
    }


    @Test
    @SuppressWarnings("unchecked")
    void updateJobStatus_succeeds_whenDocumentFound() {
        UpdateResponse<JobDocument> response = mock(UpdateResponse.class);
        when(response.result()).thenReturn(Result.Updated);
        when(elasticsearchTemplate.execute(any())).thenReturn(response);

        service.updateJobStatus("job-1", "CLOSED");

        verify(elasticsearchTemplate).execute(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateJobStatus_throwsJobDocumentNotFoundException_whenResultNotFound() {
        UpdateResponse<JobDocument> response = mock(UpdateResponse.class);
        when(response.result()).thenReturn(Result.NotFound);
        when(elasticsearchTemplate.execute(any())).thenReturn(response);

        assertThatThrownBy(() -> service.updateJobStatus("missing-job", "CLOSED"))
              .isInstanceOf(JobDocumentNotFoundException.class);
    }
}