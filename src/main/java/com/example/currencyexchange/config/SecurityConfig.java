package com.example.currencyexchange.config;

import com.example.currencyexchange.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration.
 *
 * <p>Roles:
 * <ul>
 *   <li>USER        – can access public endpoints</li>
 *   <li>ADMIN       – can add currencies and force refresh</li>
 *   <li>PREMIUM_USER – can access trend analysis</li>
 * </ul>
 *
 * <p>Users and roles are stored in PostgreSQL and loaded via {@link CustomUserDetailsService}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF protection is intentionally disabled: this is a stateless REST API using
                // HTTP Basic authentication with SessionCreationPolicy.STATELESS. No session cookies
                // are issued, so CSRF attacks (which exploit cookie-based sessions) are not applicable.
                // See: https://owasp.org/www-community/attacks/csrf ("CSRF tokens are not needed
                // if you use stateless APIs.")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .userDetailsService(userDetailsService)
                .authorizeHttpRequests(auth -> auth
                        // Actuator health
                        .requestMatchers("/actuator/health").permitAll()
                        // Swagger / OpenAPI UI
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // Authenticated user endpoints
                        .requestMatchers(HttpMethod.GET, "/api/v1/currency")
                        .hasAnyRole("USER", "PREMIUM_USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/currency/exchange-rates")
                        .hasAnyRole("USER", "PREMIUM_USER", "ADMIN")
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
