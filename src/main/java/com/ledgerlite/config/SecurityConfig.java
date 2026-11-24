package com.ledgerlite.config;

import com.ledgerlite.auth.JwtAuthenticationFilter;
import com.ledgerlite.auth.JwtService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Stateless security: JWT in an HttpOnly cookie + double-submit CSRF token.
 * No server-side sessions are ever created.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   SecurityProperties properties,
                                                   SecurityErrorHandler errorHandler) throws Exception {

        // XSRF-TOKEN cookie is readable by JS (so the SPA can echo it in X-XSRF-TOKEN).
        // The auth cookie (LL_TOKEN) is the HttpOnly one.
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .secure(properties.cookie().secure())
                .sameSite(properties.cookie().sameSite()));

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                // The default CsrfAuthenticationStrategy rotates the token (and clears the cookie)
                // whenever a request is authenticated. With JWT-per-request that happens on every
                // call. We have no session to protect, so use the no-op strategy.
                .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
                // No session exists yet on register/login, so there is nothing to ride.
                .ignoringRequestMatchers("/api/auth/register", "/api/auth/login"))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(errorHandler)
                .accessDeniedHandler(errorHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/health", "/actuator/health/**").permitAll()
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                // First layer of admin protection. AdminService re-checks the role in the database (second layer).
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, properties.cookie().name()),
                    UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
