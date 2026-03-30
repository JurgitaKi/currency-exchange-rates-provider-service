package com.example.currencyexchange.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
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
 * <p>In production, replace the in-memory user store with a database-backed one.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

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
                .authorizeHttpRequests(auth -> auth
                        // H2 console (dev only)
                        .requestMatchers("/h2-console/**").permitAll()
                        // Actuator health
                        .requestMatchers("/actuator/health").permitAll()
                        // Public endpoints
                        .requestMatchers(HttpMethod.GET, "/api/v1/currency").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/currency/exchange-rates").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers.frameOptions(Customizer.withDefaults()).disable()) // for H2 console
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Sample in-memory users for development/testing.
     * In production, use a database-backed UserDetailsService.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails user = User.builder()
                .username("user")
                .password(encoder.encode("user123"))
                .roles("USER")
                .build();

        UserDetails admin = User.builder()
                .username("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN", "USER")
                .build();

        UserDetails premiumUser = User.builder()
                .username("premium")
                .password(encoder.encode("premium123"))
                .roles("PREMIUM_USER", "USER")
                .build();

        return new InMemoryUserDetailsManager(user, admin, premiumUser);
    }
}
