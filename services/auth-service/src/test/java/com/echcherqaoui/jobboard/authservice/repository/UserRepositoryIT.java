package com.echcherqaoui.jobboard.authservice.repository;

import com.echcherqaoui.jobboard.authservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.authservice.enums.UserRole;
import com.echcherqaoui.jobboard.authservice.model.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void findByEmail_WhenUserExists_ShouldReturnUser() {
        AppUser user = createValidUser("ahmed@jobboard.com", "ahmed_dev");
        userRepository.save(user);

        Optional<AppUser> result = userRepository.findByEmail("ahmed@jobboard.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("ahmed@jobboard.com");
        assertThat(result.get().getUsername()).isEqualTo("ahmed_dev");
        assertThat(result.get().getRole()).isEqualTo(UserRole.CANDIDATE);
    }

    @Test
    void existsByEmailIgnoreCase_ShouldReturnTrue_RegardlessOfCase() {
        AppUser user = createValidUser("ahmed@jobboard.com", "ahmed_dev");
        userRepository.save(user);

        assertThat(userRepository.existsByEmailIgnoreCase("AHMED@JOBBOARD.COM")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("ahmed@jobboard.com")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("other@jobboard.com")).isFalse();
    }

    @Test
    void existsByUsernameIgnoreCase_ShouldReturnTrue_RegardlessOfCase() {
        AppUser user = createValidUser("ahmed@jobboard.com", "ahmed_dev");
        userRepository.save(user);

        assertThat(userRepository.existsByUsernameIgnoreCase("AHMED_DEV")).isTrue();
        assertThat(userRepository.existsByUsernameIgnoreCase("ahmed_dev")).isTrue();
        assertThat(userRepository.existsByUsernameIgnoreCase("unknown_user")).isFalse();
    }

    @Test
    void save_WhenDuplicateEmail_ShouldThrowDataIntegrityViolationException() {
        userRepository.saveAndFlush(createValidUser("duplicate@jobboard.com", "user1"));

        AppUser duplicateUser = createValidUser("duplicate@jobboard.com", "user2");

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicateUser))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_WhenDuplicateUsername_ShouldThrowDataIntegrityViolationException() {
        userRepository.saveAndFlush(createValidUser("user1@jobboard.com", "same_username"));

        AppUser duplicateUser = createValidUser("user2@jobboard.com", "same_username");

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicateUser))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private AppUser createValidUser(String email, String username) {
        return new AppUser()
                .setFirstName("Ahmed")
                .setLastName("Eder")
                .setEmail(email)
                .setUsername(username)
                .setPassword("encoded_password")
                .setEnabled(true)
                .setRole(UserRole.CANDIDATE);
    }
}