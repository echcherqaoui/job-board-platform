package com.echcherqaoui.jobboard.authservice.controller;

import com.echcherqaoui.jobboard.authservice.dto.CreateUserRequest;
import com.echcherqaoui.jobboard.authservice.exception.domain.PasswordMismatchException;
import com.echcherqaoui.jobboard.authservice.exception.domain.UserAlreadyExistsException;
import com.echcherqaoui.jobboard.authservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.echcherqaoui.jobboard.authservice.exception.enums.AuthErrorCode.USERNAME_ALREADY_EXISTS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = AuthController.class, properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration")
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void getLogin_Unauthenticated_ShouldReturnLoginView() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void getLogin_WithErrorQuery_ShouldAddErrorToModel() throws Exception {
        mockMvc.perform(get("/login").param("error", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "Invalid username or password"))
                .andExpect(view().name("login"));
    }

    @Test
    void getLogin_WithLogoutQuery_ShouldAddMessageToModel() throws Exception {
        mockMvc.perform(get("/login").param("logout", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("message", "You have been logged out successfully"))
                .andExpect(view().name("login"));
    }

    @Test
    void getLogin_AlreadyAuthenticated_ShouldRedirectToHome() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
              "Ahmed", null, List.of(new SimpleGrantedAuthority("ROLE_CANDIDATE"))
        );

        mockMvc.perform(get("/login").principal(auth))
              .andExpect(status().is3xxRedirection())
              .andExpect(redirectedUrl("/"));
    }

    @Test
    void getSignup_ShouldReturnSignupViewWithEmptyUserRequest() throws Exception {
        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("userRequest"))
                .andExpect(view().name("signup"));
    }

    @Test
    void processSignup_ValidForm_ShouldCreateUserAndRedirectToLogin() throws Exception {
        mockMvc.perform(post("/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "khalid@jobboard.com")
                        .param("username", "khalid_dev")
                        .param("firstName", "Khalid")
                        .param("lastName", "Echcherqaoui")
                        .param("password", "SecurePassword123")
                        .param("confirmPassword", "SecurePassword123")
                        .param("role", "RECRUITER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("successMessage", "Account created successfully! Please log in."));

        verify(userService).createUser(any(CreateUserRequest.class));
    }

    @Test
    void processSignup_ValidationErrors_ShouldReturnSignupViewWithoutCallingService() throws Exception {
        mockMvc.perform(post("/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "invalid-email")
                        .param("username", "ab") // Too short
                        .param("firstName", "")
                        .param("lastName", "")
                        .param("password", "short")
                        .param("confirmPassword", "short")
                        .param("role", "INVALID_ROLE"))
                .andExpect(status().isOk())
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("userRequest", "email", "username", "firstName", "lastName", "password", "role"))
                .andExpect(view().name("signup"));
    }

    @Test
    void processSignup_UserAlreadyExistsException_ShouldReturnSignupViewWithErrorMessage() throws Exception {
        doThrow(new UserAlreadyExistsException(USERNAME_ALREADY_EXISTS, "ahmed_dev"))
                .when(userService).createUser(any(CreateUserRequest.class));

        mockMvc.perform(post("/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "existing@jobboard.com")
                        .param("username", "existing_user")
                        .param("firstName", "Ahmed")
                        .param("lastName", "Eder")
                        .param("password", "SecurePassword123")
                        .param("confirmPassword", "SecurePassword123")
                        .param("role", "CANDIDATE"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", "User with username ahmed_dev already exists"))
                .andExpect(view().name("signup"));
    }

    @Test
    void processSignup_PasswordMismatchException_ShouldReturnSignupViewWithErrorMessage() throws Exception {
        doThrow(new PasswordMismatchException())
                .when(userService).createUser(any(CreateUserRequest.class));

        mockMvc.perform(post("/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "khalid@jobboard.com")
                        .param("username", "khalid_dev")
                        .param("firstName", "Khalid")
                        .param("lastName", "Echcherqaoui")
                        .param("password", "Password123")
                        .param("confirmPassword", "DifferentPassword123")
                        .param("role", "CANDIDATE"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", "Passwords do not match"))
                .andExpect(view().name("signup"));
    }
}