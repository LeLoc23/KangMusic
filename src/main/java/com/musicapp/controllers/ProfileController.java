package com.musicapp.controllers;

import com.musicapp.exception.WeakPasswordException;
import com.musicapp.models.User;
import com.musicapp.services.AuthService;
import com.musicapp.services.CreatorService;
import com.musicapp.services.UserService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

/**
 * C1 FIX: changePassword now delegates to UserService which uses
 * passwordEncoder.matches() for old-password verification and
 * passwordEncoder.encode() for storing the new password.
 * I1 FIX: Constructor injection.
 */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;
    private final CreatorService creatorService;
    private final AuthService authService;

    public ProfileController(UserService userService, CreatorService creatorService, AuthService authService) {
        this.userService = userService;
        this.creatorService = creatorService;
        this.authService = authService;
    }

    @GetMapping
    public String profilePage(Model model, Principal principal) {
        model.addAttribute("user", userService.getByUsername(principal.getName()));
        model.addAttribute("creatorProfile", creatorService.findByUsername(principal.getName()).orElse(null));
        return "profile";
    }

    @PostMapping("/update")
    public String updateProfile(
            @RequestParam(required = false, defaultValue = "") String fullName,
            @RequestParam(required = false, defaultValue = "") String phoneNumber,
            Principal principal) {
        userService.updateProfile(principal.getName(), fullName, phoneNumber);
        return "redirect:/profile?profileUpdated=true";
    }

    @PostMapping("/creator/request")
    public String requestCreator(
            @RequestParam(required = false, defaultValue = "") String stageName,
            @RequestParam(required = false, defaultValue = "") String bio,
            Principal principal) {
        creatorService.requestCreator(principal.getName(), stageName, bio);
        return "redirect:/profile?creatorRequested=true";
    }

    @PostMapping("/change-password")
    public String changePassword(
            @RequestParam String oldPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Principal principal) {

        try {
            userService.changePassword(principal.getName(), oldPassword, newPassword, confirmPassword);
            return "redirect:/profile?success=true";
        } catch (BadCredentialsException e) {
            return "redirect:/profile?error=old_wrong";
        } catch (WeakPasswordException e) {
            return "redirect:/profile?error=weak_pass";
        } catch (IllegalArgumentException e) {
            return "redirect:/profile?error=mismatch";
        }
    }

    @PostMapping("/verify-email")
    public String verifyEmail(@RequestParam String code, Principal principal) {
        boolean success = authService.verifyEmail(principal.getName(), code);
        if (success) {
            return "redirect:/profile?verified=true";
        } else {
            return "redirect:/profile?error=verification_failed";
        }
    }

    @PostMapping("/resend-verification")
    public String resendVerification(Principal principal) {
        User user = userService.getByUsername(principal.getName());
        authService.sendEmailVerification(user);
        return "redirect:/profile?verificationSent=true";
    }
}
