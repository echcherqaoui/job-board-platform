package com.echcherqaoui.jobboard.authservice.controller;

import com.echcherqaoui.jobboard.authservice.dto.CreateUserRequest;
import com.echcherqaoui.jobboard.authservice.exception.domain.PasswordMismatchException;
import com.echcherqaoui.jobboard.authservice.exception.domain.UserAlreadyExistsException;
import com.echcherqaoui.jobboard.authservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final UserService userService;

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        Authentication authentication,
                        Model model) {

        // If the user is already authenticated, don't show the login page again
        if (authentication != null && authentication.isAuthenticated())
            return "redirect:/";

        if (error != null)
            model.addAttribute("error", "Invalid username or password");

        if (logout != null)
            model.addAttribute("message", "You have been logged out successfully");
        
        return "login";
    }

    @GetMapping("/signup")
    public String showSignupForm(Model model) {
        if (!model.containsAttribute("userRequest"))
            model.addAttribute("userRequest", new CreateUserRequest());

        return "signup";
    }

    @PostMapping("/signup")
    public String processSignup(@Valid @ModelAttribute("userRequest") CreateUserRequest userRequest,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes,
                                Model model) {

        if (bindingResult.hasErrors())
            return "signup";

        try {
            userService.createUser(userRequest);
            redirectAttributes.addFlashAttribute(
                  "successMessage",
                  "Account created successfully! Please log in."
            );
            return "redirect:/login";
        } catch (UserAlreadyExistsException | PasswordMismatchException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "signup";
        } catch (Exception e) {
            log.error("Unexpected error during user registration", e);
            model.addAttribute("errorMessage", "An unexpected error occurred. Please try again.");
            return "signup";
        }
    }
}