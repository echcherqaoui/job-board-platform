package com.echcherqaoui.jobboard.searchservice.repository;

import com.echcherqaoui.jobboard.searchservice.document.JobDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobDocumentRepository extends ElasticsearchRepository<JobDocument, String> {
}
