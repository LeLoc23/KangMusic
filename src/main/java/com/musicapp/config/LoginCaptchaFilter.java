package com.musicapp.config;

import com.musicapp.services.ImageCaptchaService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class LoginCaptchaFilter extends OncePerRequestFilter {

    private static final AntPathRequestMatcher LOGIN_POST_MATCHER = new AntPathRequestMatcher("/login", "POST");

    private final ImageCaptchaService captchaService;

    public LoginCaptchaFilter(ImageCaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!LOGIN_POST_MATCHER.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        String submittedCode = request.getParameter("captchaCode");
        String captchaToken = request.getParameter(ImageCaptchaService.TOKEN_REQUEST_PARAM);

        if (!captchaService.verifyAndConsume(session, submittedCode, captchaToken)) {
            response.sendRedirect(request.getContextPath() + "/login?captchaError");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
