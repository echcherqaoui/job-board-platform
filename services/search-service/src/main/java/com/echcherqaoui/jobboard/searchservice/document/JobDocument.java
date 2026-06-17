package com.echcherqaoui.jobboard.searchservice.document;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.Instant;
import java.util.List;

@Document(indexName = "jobs")
@Setting(settingPath = "elasticsearch/settings.json")
@Mapping(mappingPath = "elasticsearch/mappings.json")
@Getter
@Setter
@Accessors(chain = true)
public class JobDocument {

    @Id
    private String id;

    private String recruiterId;

    private String companyName;

    private String companyLogo;

    private String title;

    private String description;

    private String requirements;

    private String location;

    private String workModality;

    private String jobType;

    private String experienceLevel;

    private String status;

    private Double salaryMin;

    private Double salaryMax;

    private String currency;

    private List<String> skills;

    private Instant createdAt;

    private Instant expiresAt;
}
