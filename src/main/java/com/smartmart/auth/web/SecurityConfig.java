package com.smartmart.auth.web;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults;

import java.time.Clock;
import java.util.LinkedHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.smartmart.auth.service.AccountDetailsService;

/**
 * Session login with the form on {@code /login}, and access by role
 * (GUIDELINES.md section 7, FR-AUTH-5). A failed login returns to
 * {@code /login?error}; logging out returns to {@code /login?logout}. A
 * successful login always goes to the start page for the user's role. After
 * five failed logins in a minute for one username and address, further
 * attempts get 429 until the minute has passed ({@link FailedLoginLimiter}).
 * <p>
 * Every page is listed with the roles that may open it; anything not listed
 * is denied. Roles are reloaded on every request ({@link FreshRolesFilter}).
 * Actuator (SCRUM-59): health, liveness and readiness are public and show
 * only UP or DOWN; metrics are for administrators. The Prometheus endpoint has
 * its own chain with HTTP Basic for the scraper, {@code prometheus}, whose
 * password comes from {@code METRICS_PASSWORD}; without one, it is closed.
 * <p>
 * A logged-in user with the wrong role gets 403. A visitor who is
 * not logged in is sent to the login page, or gets 401 from {@code /api/}.
 */
@Configuration
class SecurityConfig {

    static final int BCRYPT_STRENGTH = 12;

    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";
    private static final String CASHIER = "CASHIER";
    private static final String INVENTORY_STAFF = "INVENTORY_STAFF";
    private static final String METRICS = "METRICS";
    private static final String SCRAPER = "prometheus";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    FailedLoginLimiter failedLoginLimiter() {
        return new FailedLoginLimiter(Clock.systemUTC());
    }

    /** Only the Prometheus scraper, with HTTP Basic and no session (SCRUM-60). */
    @Bean
    RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(ADMIN).implies(MANAGER, INVENTORY_STAFF, CASHIER)
                .build();
    }

    /** Only the Prometheus scraper, with HTTP Basic and no session (SCRUM-60). */
    @Bean
    @Order(1)
    SecurityFilterChain metricsScrapeChain(HttpSecurity http, PasswordEncoder encoder,
            @Value("${smartmart.monitoring.metrics-password:}") String password) throws Exception {
        InMemoryUserDetailsManager scraper = new InMemoryUserDetailsManager();
        if (!password.isBlank()) {
            scraper.createUser(User.withUsername(SCRAPER).password(encoder.encode(password)).roles(METRICS).build());
        }
        http.securityMatcher("/actuator/prometheus")
                .authorizeHttpRequests(requests -> requests.anyRequest().hasRole(METRICS))
                .userDetailsService(scraper)
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountDetailsService accounts,
            FailedLoginLimiter limiter) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                        .requestMatchers("/css/**", "/js/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/metrics/**").hasRole(ADMIN)
                        .requestMatchers("/").authenticated()
                        .requestMatchers("/users/**").hasRole(ADMIN)
                        .requestMatchers("/pos/**").hasAnyRole(CASHIER, ADMIN)
                        .requestMatchers("/categories/**").hasAnyRole(INVENTORY_STAFF, ADMIN)
                        .requestMatchers(HttpMethod.GET, "/products/new", "/products/*/edit").hasAnyRole(INVENTORY_STAFF, ADMIN)
                        .requestMatchers(HttpMethod.GET, "/products", "/products/*").hasAnyRole(INVENTORY_STAFF, MANAGER, ADMIN)
                        .requestMatchers("/products/**").hasAnyRole(INVENTORY_STAFF, ADMIN)
                        .requestMatchers("/suppliers/**").hasAnyRole(INVENTORY_STAFF, ADMIN)
                        // Purchase orders (SCRUM-54): staff raise and receive, managers approve, cancel and close.
                        .requestMatchers(HttpMethod.POST, "/purchase-orders/*/submit", "/purchase-orders/*/cancel",
                                "/purchase-orders/*/close").hasAnyRole(MANAGER, ADMIN)
                        .requestMatchers("/purchase-orders/new", "/purchase-orders/*/receive").hasAnyRole(INVENTORY_STAFF, ADMIN)
                        .requestMatchers(HttpMethod.POST, "/purchase-orders").hasAnyRole(INVENTORY_STAFF, ADMIN)
                        .requestMatchers(HttpMethod.GET, "/purchase-orders", "/purchase-orders/*").hasAnyRole(INVENTORY_STAFF, MANAGER, ADMIN)
                        .requestMatchers("/alerts/**").hasAnyRole(INVENTORY_STAFF, MANAGER, ADMIN)
                        .requestMatchers("/dashboard/**").hasAnyRole(ADMIN, MANAGER, CASHIER, INVENTORY_STAFF)
                        .requestMatchers("/reports/**").hasAnyRole(MANAGER, ADMIN)
                        .anyRequest().denyAll())
                .addFilterBefore(new FreshRolesFilter(accounts), AnonymousAuthenticationFilter.class)
                .addFilterBefore(new LoginLimitFilter(limiter), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(notLoggedIn()))
                .formLogin(form -> form.loginPage("/login")
                        .successHandler(loggedIn(limiter))
                        .failureHandler(loginFailed(limiter))
                        .permitAll())
                .logout(logout -> logout.permitAll());
        return http.build();
    }

    /** Always the role's start page: a saved page may belong to another role (SCRUM-52). */
    private static AuthenticationSuccessHandler loggedIn(FailedLoginLimiter limiter) {
        SimpleUrlAuthenticationSuccessHandler startPage = new SimpleUrlAuthenticationSuccessHandler("/");
        return (request, response, authentication) -> {
            limiter.recordSuccess(LoginLimitFilter.username(request), request.getRemoteAddr());
            startPage.onAuthenticationSuccess(request, response, authentication);
        };
    }

    private static AuthenticationFailureHandler loginFailed(FailedLoginLimiter limiter) {
        SimpleUrlAuthenticationFailureHandler loginPage = new SimpleUrlAuthenticationFailureHandler("/login?error");
        return (request, response, exception) -> {
            limiter.recordFailure(LoginLimitFilter.username(request), request.getRemoteAddr());
            loginPage.onAuthenticationFailure(request, response, exception);
        };
    }

    private AuthenticationEntryPoint notLoggedIn() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> byPath = new LinkedHashMap<>();
        byPath.put(withDefaults().matcher("/api/**"), new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(byPath);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
        return entryPoint;
    }
}
