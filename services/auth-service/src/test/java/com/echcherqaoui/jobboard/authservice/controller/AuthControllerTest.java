package com.echcherqaoui.jobboard.authservice.controller;

import com.echcherqaoui.jobboard.authservice.exception.domain.PasswordMismatchException;
import com.echcherqaoui.jobboard.authservice.exception.domain.UserAlreadyExistsException;
import com.echcherqaoui.jobboard.authservice.exception.enums.AuthErrorCode;
import com.echcherqaoui.jobboard.authservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc
@ImportAutoConfiguration(exclude = {
      HibernateJpaAutoConfiguration.class,
      DataSourceAutoConfiguration.class,
      FlywayAutoConfiguration.class,
      SecurityAutoConfiguration.class,
      SecurityFilterAutoConfiguration.class,
      OAuth2ClientWebSecurityAutoConfiguration.class,
      OAuth2ResourceServerAutoConfiguration.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void login_unauthenticated_returnsLoginView() throws Exception {
        mockMvc.perform(get("/login"))
              .andExpect(status().isOk())
              .andExpect(view().name("login"))
              .andExpect(model().attributeDoesNotExist("error"))
              .andExpect(model().attributeDoesNotExist("message"));
    }

    @Test
    void login_alreadyAuthenticated_redirectsHome() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken("ahmed", "pass", List.of());

        mockMvc.perform(get("/login").principal(auth))
              .andExpect(status().is3xxRedirection())
              .andExpect(redirectedUrl("/"));
    }

    @Test
    void login_withError_addsErrorAttribute() throws Exception {
        mockMvc.perform(get("/login").param("error", "true"))
              .andExpect(view().name("login"))
              .andExpect(model().attribute("error", "Invalid username or password"));
    }

    @Test
    void login_withLogout_addsMessageAttribute() throws Exception {
        mockMvc.perform(get("/login").param("logout", "true"))
              .andExpect(view().name("login"))
              .andExpect(model().attribute("message", "You have been logged out successfully"));
    }

    @Test
    void showSignupForm_addsFreshUserRequest() throws Exception {
        mockMvc.perform(get("/signup"))
              .andExpect(status().isOk())
              .andExpect(view().name("signup"))
              .andExpect(model().attributeExists("userRequest"));
    }

    @Test
    void processSignup_validationErrors_returnsSignupView() throws Exception {
        mockMvc.perform(post("/signup"))
              // no fields set — should fail @Valid constraints on CreateUserRequest
              .andExpect(view().name("signup"));

        verifyNoInteractions(userService);
    }

    @Test
    void processSignup_success_redirectsToLoginWithFlashMessage() throws Exception {
        mockMvc.perform(post("/signup")
                    .param("email", "ahmed@example.com")
                    .param("username", "ahmed")
                    .param("firstName", "Ahmed")
                    .param("lastName", "EDER")
                    .param("password", "pass1234")
                    .param("confirmPassword", "pass1234")
                    .param("role", "CANDIDATE"))
              .andExpect(status().is3xxRedirection())
              .andExpect(redirectedUrl("/login"))
              .andExpect(flash().attributeExists("successMessage"));

        verify(userService).createUser(any());
    }

    @Test
    void processSignup_userAlreadyExists_returnsSignupWithErrorMessage() throws Exception {
        doThrow(new UserAlreadyExistsException(AuthErrorCode.EMAIL_ALREADY_EXISTS, "ahmed@example.com"))
              .when(userService).createUser(any());

        mockMvc.perform(post("/signup")
                    .param("email", "ahmed@example.com")
                    .param("username", "ahmed")
                    .param("firstName", "Ahmed")
                    .param("lastName", "EDER")
                    .param("password", "pass1234")
                    .param("confirmPassword", "pass1234")
                    .param("role", "CANDIDATE"))
              .andExpect(view().name("signup"))
              .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    void processSignup_passwordMismatch_returnsSignupWithErrorMessage() throws Exception {
        doThrow(new PasswordMismatchException())
              .when(userService).createUser(any());

        mockMvc.perform(post("/signup")
                    .param("email", "ahmed@example.com")
                    .param("username", "ahmed")
                    .param("firstName", "Ahmed")
                    .param("lastName", "EDER")
                    .param("password", "pass1234")
                    .param("confirmPassword", "pass5678")
                    .param("role", "CANDIDATE"))
              .andExpect(view().name("signup"))
              .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    void processSignup_unexpectedException_returnsGenericErrorMessage() throws Exception {
        doThrow(new RuntimeException("db connection refused"))
              .when(userService).createUser(any());

        mockMvc.perform(post("/signup")
                    .param("email", "ahmed@example.com")
                    .param("username", "ahmed")
                    .param("firstName", "Ahmed")
                    .param("lastName", "EDER")
                    .param("password", "pass1234")
                    .param("confirmPassword", "pass1234")
                    .param("role", "CANDIDATE"))
              .andExpect(view().name("signup"))
              .andExpect(model().attribute("errorMessage", "An unexpected error occurred. Please try again."));
    }
}