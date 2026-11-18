/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.security;

import com.kismetsoftware.politigif.config.logging.RequestTraceFilter;
import com.kismetsoftware.politigif.config.web.filter.CspNonceFilter;
import com.kismetsoftware.politigif.config.web.filter.HttpsRedirectFilter;
import com.kismetsoftware.politigif.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * One chain. Public: the feed, share pages, the converted media, reporting, sign-in, sitemap.
 * Signed-in: upload, the seedling page, account. Admin: the report queue.
 *
 * <p>Ported from fieldbook (plainsight lineage) with the 2026-08-24 fixes: CSP owned by
 * {@link CspNonceFilter}, {@link HttpsRedirectFilter} ahead of everything, static assets exempt from
 * no-store, and {@code denyAll()} for anything unlisted. There is no form login and no password:
 * {@link SessionSignIn} creates the Authentication after an emailed link is redeemed. With no
 * login form there is nothing to return to after sign-in, so the request cache is off.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String PERMISSIONS_POLICY = String.join(", ",
            "camera=()",
            "microphone=()",
            "geolocation=()",
            "payment=()",
            "usb=()",
            "interest-cohort=()");

    /** Anything not listed here is denied: see {@code anyRequest().denyAll()} at the bottom. */
    private static void securityPaths(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authHttp) {
        authHttp.requestMatchers("/error").permitAll();

        // ---- static assets (StaticAssetsReachableTest walks static/ against this list) ----
        authHttp.requestMatchers(HttpMethod.GET, "/css/**").permitAll();
        authHttp.requestMatchers(HttpMethod.GET, "/js/**").permitAll();
        authHttp.requestMatchers(HttpMethod.GET, "/fonts/**").permitAll();
        authHttp.requestMatchers(HttpMethod.GET, "/favicon.ico").permitAll();
        authHttp.requestMatchers(HttpMethod.GET, "/favicon.svg").permitAll();
        authHttp.requestMatchers(HttpMethod.GET, "/robots.txt").permitAll();
        authHttp.requestMatchers(HttpMethod.GET, "/sitemap.xml").permitAll();
        authHttp.requestMatchers(HttpMethod.GET, "/.well-known/**").permitAll();

        // ---- signing in: the emailed link and its browser nonce are the proof ----
        authHttp.requestMatchers("/signin", "/signin/**").permitAll();

        // ---- public: every GIF is public ----
        authHttp.requestMatchers(HttpMethod.GET, "/").permitAll();
        authHttp.requestMatchers(HttpMethod.GET, "/g/**").permitAll();
        authHttp.requestMatchers(HttpMethod.POST, "/g/*/report").permitAll();

        // ---- signed in ----
        authHttp.requestMatchers("/upload").authenticated();
        authHttp.requestMatchers("/seedling").authenticated();
        authHttp.requestMatchers("/account", "/account/**").authenticated();

        // ---- admin ----
        authHttp.requestMatchers("/admin", "/admin/**").hasRole("ADMIN");

        authHttp.anyRequest().denyAll();
    }

    @Bean
    public SecurityFilterChain webFilterChain(HttpSecurity httpSecurity, UserRepository userRepository,
                                              SessionSignIn sessionSignIn) {
        return httpSecurity
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterAfter(new RequestTraceFilter(), SecurityContextHolderFilter.class)
                // Re-checks the account on every request: a reset or deleted account ends every
                // other session now, and an account with no seedling yet is held at /seedling.
                .addFilterAfter(new AccountStatusFilter(userRepository, sessionSignIn), RequestTraceFilter.class)
                .headers(headers -> {
                    // CSP is written by CspNonceFilter: never re-add it here.
                    headers.permissionsPolicyHeader(policy -> policy.policy(PERMISSIONS_POLICY));
                    headers.referrerPolicy(referrer -> referrer.policy(
                            ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.cacheControl(HeadersConfigurer.CacheControlConfig::disable);
                    headers.addHeaderWriter(dynamicPageCacheControlWriter());
                    headers.httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31536000));
                })
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/signin")))
                .logout(logout -> {
                    logout.logoutUrl("/signout");
                    logout.logoutSuccessUrl("/");
                })
                .authorizeHttpRequests(SecurityConfig::securityPaths)
                .build();
    }

    @Bean
    public FilterRegistrationBean<HttpsRedirectFilter> httpsRedirectFilter(
            @Value("${app.security.https-redirect.enabled:true}") boolean enabled) {
        var registration = new FilterRegistrationBean<>(new HttpsRedirectFilter(enabled));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }

    /** Outside the Security chain so it also runs on the ERROR dispatch (error views render fragments). */
    @Bean
    public FilterRegistrationBean<CspNonceFilter> cspNonceFilter() {
        var registration = new FilterRegistrationBean<>(new CspNonceFilter());
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(jakarta.servlet.DispatcherType.REQUEST,
                jakarta.servlet.DispatcherType.ERROR);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    /** Security's no-store header on every response EXCEPT true static assets. */
    static HeaderWriter dynamicPageCacheControlWriter() {
        return new DelegatingRequestMatcherHeaderWriter(
                new NegatedRequestMatcher(staticAssetMatcher()), new CacheControlHeadersWriter());
    }

    /**
     * The true static assets. Media under {@code /g/} is deliberately NOT here: it is converted
     * fresh on every request and nothing is cached ("we ain't Reddit").
     */
    static RequestMatcher staticAssetMatcher() {
        return new OrRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher("/css/**"),
                PathPatternRequestMatcher.withDefaults().matcher("/js/**"),
                PathPatternRequestMatcher.withDefaults().matcher("/fonts/**"),
                PathPatternRequestMatcher.withDefaults().matcher("/favicon.ico"),
                PathPatternRequestMatcher.withDefaults().matcher("/favicon.svg"),
                PathPatternRequestMatcher.withDefaults().matcher("/robots.txt"));
    }

    /** BCrypt for seedlings. There are no passwords in this app. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
