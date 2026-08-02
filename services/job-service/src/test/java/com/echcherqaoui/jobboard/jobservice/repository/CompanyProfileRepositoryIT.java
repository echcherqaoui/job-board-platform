package com.echcherqaoui.jobboard.jobservice.repository;

import com.echcherqaoui.jobboard.jobservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CompanyProfileRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    @BeforeEach
    void setUp() {
        companyProfileRepository.deleteAll();
    }

    @Test
    void findCompanyNameByRecruiterId_WhenCompanyExists_ShouldReturnCompanyName() {
        UUID recruiterId = UUID.randomUUID();
        CompanyProfile profile = new CompanyProfile()
              .setRecruiterId(recruiterId)
              .setCompanyName("Acme Corp")
              .setCompanyLogo("logo.png")
              .setLastEventId("evt-1")
              .setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        companyProfileRepository.save(profile);

        Optional<String> result = companyProfileRepository.findCompanyNameByRecruiterId(recruiterId);

        assertThat(result).isPresent().contains("Acme Corp");
    }

    @Test
    void findCompanyNameByRecruiterId_WhenCompanyDoesNotExist_ShouldReturnEmpty() {
        Optional<String> result = companyProfileRepository.findCompanyNameByRecruiterId(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void findByRecruiterIdIn_ShouldReturnProfilesMatchingRecruiterIds() {
        UUID recruiterId1 = UUID.randomUUID();
        UUID recruiterId2 = UUID.randomUUID();
        UUID recruiterId3 = UUID.randomUUID();

        CompanyProfile profile1 = new CompanyProfile()
              .setRecruiterId(recruiterId1)
              .setCompanyName("Acme Corp")
              .setCompanyLogo("logo1.png")
              .setLastEventId("evt-1")
              .setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        CompanyProfile profile2 = new CompanyProfile()
              .setRecruiterId(recruiterId2)
              .setCompanyName("Beta LLC")
              .setCompanyLogo("logo2.png")
              .setLastEventId("evt-2")
              .setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        CompanyProfile profile3 = new CompanyProfile()
              .setRecruiterId(recruiterId3)
              .setCompanyName("Gamma Inc")
              .setCompanyLogo("logo3.png")
              .setLastEventId("evt-3")
              .setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        companyProfileRepository.saveAll(List.of(profile1, profile2, profile3));

        List<CompanyProfile> results = companyProfileRepository.findByRecruiterIdIn(List.of(recruiterId1, recruiterId2));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(CompanyProfile::getCompanyName)
              .containsExactlyInAnyOrder("Acme Corp", "Beta LLC");
    }
}