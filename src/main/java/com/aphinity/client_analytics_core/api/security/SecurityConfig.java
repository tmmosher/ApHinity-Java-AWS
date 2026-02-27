package com.aphinity.client_analytics_core.api.security;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.aphinity.client_analytics_core.api.auth.properties.LoginAttemptProperties;
import com.digitalsanctuary.cf.turnstile.TurnstileConfiguration;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import org.springframework.context.annotation.Import;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;


/**
 * Spring Security configuration for browser and API authentication flows.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, LoginAttemptProperties.class})
@Import(TurnstileConfiguration.class)
public class SecurityConfig {
    /**
     * Builds the application security filter chain.
     *
     * @param http security builder
     * @param jwtAuthenticationConverter JWT authority converter
     * @param bearerTokenResolver token resolver supporting header/cookie sources
     * @param accessTokenRefreshFilter pre-auth token refresh filter
     * @param csrfCookieFilter CSRF cookie materialization filter
     * @param apiAuthenticationEntryPoint API 401 entry point
     * @return configured security filter chain
     * @throws Exception when configuration fails
     */
    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationConverter jwtAuthenticationConverter,
        BearerTokenResolver bearerTokenResolver,
        AccessTokenRefreshFilter accessTokenRefreshFilter,
        CookieCsrfTokenRepository csrfTokenRepository,
        CsrfCookieFilter csrfCookieFilter,
        CoreApiCsrfEnforcementFilter coreApiCsrfEnforcementFilter,
        ApiAuthenticationEntryPoint apiAuthenticationEntryPoint
    ) throws Exception {
        AuthenticationEntryPoint authEntryPoint = getAuthenticationEntryPoint(apiAuthenticationEntryPoint);
        http
            // APIs are stateless and rely on JWT/cookie tokens rather than server sessions.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/assets/**",
                    "/favicon.ico",
                    "/error",
                    "/login",
                    "/signup",
                    "/support",
                    "/recovery",
                    "/verify"
                ).permitAll()
                .requestMatchers("/api/core/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authEntryPoint)
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(bearerTokenResolver)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .ignoringRequestMatchers(
                    "/api/auth/login",
                    "/api/auth/signup",
                    "/api/auth/recovery",
                    "/api/auth/verify"
                )
            )
            .headers(headers ->
                headers.addHeaderWriter((request, response) -> {
                    String nonce = CspNonceSupport.getOrCreateNonce(request);
                    response.setHeader(
                        "Content-Security-Policy",
                        ContentSecurityPolicyBuilder.buildPolicy(nonce)
                    );
                })
            )
            .addFilterAfter(csrfCookieFilter, CsrfFilter.class)
            .addFilterAfter(coreApiCsrfEnforcementFilter, CsrfCookieFilter.class)
            .addFilterBefore(accessTokenRefreshFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * @return cookie-backed CSRF repository used by the frontend double-submit flow
     */
    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookiePath("/");
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));
        return csrfTokenRepository;
    }

    /**
     * @param csrfTokenRepository shared CSRF token repository
     * @return core API CSRF enforcement filter
     */
    @Bean
    CoreApiCsrfEnforcementFilter coreApiCsrfEnforcementFilter(
        CookieCsrfTokenRepository csrfTokenRepository,
        AsyncLogService asyncLogService
    ) {
        return new CoreApiCsrfEnforcementFilter(csrfTokenRepository, asyncLogService);
    }

    /**
     * Chooses API JSON 401 responses for API paths and redirect behavior for browser paths.
     *
     * @param apiAuthenticationEntryPoint API entry point
     * @return composed authentication entry point
     */
    private static @NonNull AuthenticationEntryPoint getAuthenticationEntryPoint(ApiAuthenticationEntryPoint apiAuthenticationEntryPoint) {
        RedirectAuthenticationEntryPoint redirectEntryPoint =
            new RedirectAuthenticationEntryPoint("/login");
        return (request, response, authException) -> {
            String path = request.getRequestURI();
            // API callers expect JSON responses while browser users should be redirected.
            boolean isApiPath = path.equals("/api")
                || path.startsWith("/api/")
                || path.equals("/core")
                || path.startsWith("/core/");
            if (isApiPath) {
                apiAuthenticationEntryPoint.commence(request, response, authException);
                return;
            }
            redirectEntryPoint.commence(request, response, authException);
        };
    }

    /**
     * @return BCrypt password encoder
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * @param properties JWT configuration properties
     * @return HS256 JWT encoder
     */
    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        SecretKey secretKey = new SecretKeySpec(
            properties.getSecret().getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
        );
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    /**
     * @param properties JWT configuration properties
     * @return HS256 JWT decoder
     */
    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey secretKey = new SecretKeySpec(
            properties.getSecret().getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
        );
        return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /**
     * @return JWT authentication converter that maps roles claim to ROLE_* authorities
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }

    /**
     * @return bearer token resolver that falls back to access-token cookies
     */
    @Bean
    BearerTokenResolver bearerTokenResolver() {
        return new CookieBearerTokenResolver();
    }
}
