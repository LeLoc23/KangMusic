package com.musicapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * CRIT-2 FIX: CSRF re-enabled for ALL endpoints including /api/**
 *   - CookieCsrfTokenRepository.withHttpOnlyFalse() sets an XSRF-TOKEN cookie
 *     that JavaScript can read and send as X-XSRF-TOKEN header.
 *   - Only /h2-console/** remains exempt (H2 console can't send custom headers).
 *   - fetch() POST calls in app.js now include the CSRF token header.
 *
 * Actuator security: /actuator/health is public; /actuator/** requires ADMIN.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CRIT-2 FIX: Use cookie-based CSRF — JS reads XSRF-TOKEN cookie and sends
            // as X-XSRF-TOKEN header on every state-changing fetch() POST call.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/h2-console/**") // Only H2 console (can't send headers)
            )
            // Allow H2 console to render in an iframe from same origin only
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            )
            .authorizeHttpRequests(auth -> auth
                // SECURITY: H2 console is ADMIN-only. Enabled only in dev profile.
                .requestMatchers("/h2-console/**").hasAuthority("ROLE_ADMIN")
                // Actuator: health check is public; all other actuator endpoints require ADMIN
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasAuthority("ROLE_ADMIN")
                .requestMatchers("/", "/login", "/register", "/forgot-password",
                        "/reset-password", "/css/**", "/js/**", "/media/**",
                        "/stream/**", "/api/search", "/api/genre/**", "/track/**").permitAll()
                .requestMatchers("/admin/**").hasAuthority("ROLE_ADMIN")
                .requestMatchers("/add").hasAuthority("ROLE_ADMIN")
                .requestMatchers("/playlists/**", "/library").authenticated()
                .requestMatchers("/api/library/**", "/api/my-playlists", "/api/play/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(customSuccessHandler())
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler customSuccessHandler() {
        return (request, response, authentication) -> {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            response.sendRedirect(isAdmin ? "/admin" : "/");
        };
    }
}