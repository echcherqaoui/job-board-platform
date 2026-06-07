package com.echcherqaoui.jobboard.jobservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "company_profiles")
@Getter
@Setter
@Accessors(chain = true)
public class CompanyProfile {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID recruiterId;

    @Column(nullable = false)
    private String companyName;

    private String companyLogo;

    @Column(nullable = false)
    private String lastEventId;

    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;
}