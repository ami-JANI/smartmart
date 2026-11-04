package com.smartmart.auth.web;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults;

import java.util.LinkedHashMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Session login with the form on {@code /login}, and access by role
 * (GUIDELINES.md section 7, FR-AUTH-5). A failed login returns to
 * {@code /login?error}; logging out returns to {@code /login?logout}.
 * <p>
 * Every page is listed with the roles that may open it; anything not listed
 * is denied. A logged-in user with the wrong role gets 403. A visitor who is
 * not logged in is sent to the login page, or gets 401 from {@code /api/}.
 */
@Configuration
class SecurityConfig {

    static final int BCRYPT_STRENGTH = 12;

    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";
    private static final String CASHIER = "CASHIER";
    private static final String INVENTORY_STAFF = "INVENTORY_STAFF";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                        .requestMatchers("/css/**", "/js/**", "/error").permitAll()
                        .requestMatchers("/").authenticated()
                        .requestMatchers("/users/**").hasRole(ADMIN)
                        .requestMatchers("/pos/**").hasRole(CASHIER)
                        .requestMatchers("/categories/**").hasRole(INVENTORY_STAFF)
                        .requestMatchers(HttpMethod.GET, "/products/new", "/products/*/edit").hasRole(INVENTORY_STAFF)
                        .requestMatchers(HttpMethod.GET, "/products", "/products/*").hasAnyRole(INVENTORY_STAFF, MANAGER)
                        .requestMatchers("/products/**").hasRole(INVENTORY_STAFF)
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(notLoggedIn()))
                .formLogin(form -> form.loginPage("/login").permitAll())
                .logout(logout -> logout.permitAll());
        return http.build();
    }

    private AuthenticationEntryPoint notLoggedIn() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> byPath = new LinkedHashMap<>();
        byPath.put(withDefaults().matcher("/api/**"), new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(byPath);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
        return entryPoint;
    }
}
