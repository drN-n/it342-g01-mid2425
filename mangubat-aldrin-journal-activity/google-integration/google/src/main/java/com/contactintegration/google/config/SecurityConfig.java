package com.contactintegration.google.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/css/**", "/js/**", "/images/**").permitAll() // Allow static resources
                        .requestMatchers("/h2-console/**").permitAll() // Allow H2 console for development
                        .requestMatchers("/contacts/**").authenticated() // Protect CRUD endpoints
                        .anyRequest().authenticated() // All other requests require authentication
                )
                .oauth2Login(oauth2Login ->
                        oauth2Login
                                .loginPage("/oauth2/authorization/google") // Redirect to Google login if not authenticated
                                .defaultSuccessUrl("/google/contacts", true) // Redirect to fetch Google contacts after successful login
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/") // Redirect to home page after logout
                        .permitAll()
                );

        // For H2 console to work with Spring Security
        http.csrf().disable();
        http.headers().frameOptions().disable();

        return http.build();
    }
}