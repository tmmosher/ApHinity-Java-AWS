package com.aphinity.client_analytics_core.api.security;

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
import org.springframework.context.annotation.Import;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;


@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, LoginAttemptProperties.class})
@Import(TurnstileConfiguration.class)
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationConverter jwtAuthenticationConverter,
        BearerTokenResolver bearerTokenResolver,
        AccessTokenRefreshFilter accessTokenRefreshFilter,
        CsrfCookieFilter csrfCookieFilter,
        ApiAuthenticationEntryPoint apiAuthenticationEntryPoint
    ) throws Exception {
        RedirectAuthenticationEntryPoint redirectEntryPoint =
            new RedirectAuthenticationEntryPoint("/login");
        AuthenticationEntryPoint authEntryPoint = (request, response, authException) -> {
            String path = request.getRequestURI();
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
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookiePath("/");
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .ignoringRequestMatchers(
                    "/api/auth/login",
                    "/api/auth/signup",
                    "/api/auth/recovery",
                    "/api/auth/verify"
                )
            )
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
            .addFilterAfter(csrfCookieFilter, CsrfFilter.class)
            .addFilterBefore(accessTokenRefreshFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        SecretKey secretKey = new SecretKeySpec(
            properties.getSecret().getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
        );
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey secretKey = new SecretKeySpec(
            properties.getSecret().getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
        );
        return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return converter;
    }

    @Bean
    BearerTokenResolver bearerTokenResolver() {
        return new CookieBearerTokenResolver();
    }
}
