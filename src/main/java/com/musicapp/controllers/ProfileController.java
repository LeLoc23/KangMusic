package com.musicapp.controllers;

import com.musicapp.exception.WeakPasswordException;
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

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String profilePage(Model model, Principal principal) {
        model.addAttribute("user", userService.getByUsername(principal.getName()));
        return "profile";
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
}