package com.aphinity.client_analytics_core.api.integration;

import com.aphinity.client_analytics_core.api.auth.AuthCookieNames;
import com.aphinity.client_analytics_core.api.auth.entities.AppUser;
import com.aphinity.client_analytics_core.api.auth.entities.Role;
import com.aphinity.client_analytics_core.api.auth.repositories.AppUserRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.AuthSessionRepository;
import com.aphinity.client_analytics_core.api.auth.repositories.RoleRepository;
import com.aphinity.client_analytics_core.api.auth.services.MailSendingService;
import com.aphinity.client_analytics_core.api.core.entities.Location;
import com.aphinity.client_analytics_core.api.core.entities.LocationUser;
import com.aphinity.client_analytics_core.api.core.entities.LocationUserId;
import com.aphinity.client_analytics_core.api.core.repositories.LocationRepository;
import com.aphinity.client_analytics_core.api.core.repositories.LocationUserRepository;
import com.aphinity.client_analytics_core.api.security.JwtProperties;
import com.aphinity.client_analytics_core.logging.AsyncLogService;
import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.HttpCookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.profiles.active=integration")
@AutoConfigureMockMvc
abstract class AbstractApiIntegrationTest {
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected AppUserRepository appUserRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected AuthSessionRepository authSessionRepository;

    @Autowired
    protected LocationRepository locationRepository;

    @Autowired
    protected LocationUserRepository locationUserRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected JwtEncoder jwtEncoder;

    @Autowired
    protected JwtProperties jwtProperties;

    @MockitoBean
    protected TurnstileValidationService turnstileValidationService;

    @MockitoBean
    protected MailSendingService mailSendingService;

    @MockitoBean
    protected AsyncLogService asyncLogService;

    @BeforeEach
    void resetState() {
        resetDatabase();
        seedRoles();
        lenient().when(turnstileValidationService.validateTurnstileResponse(anyString(), anyString()))
            .thenReturn(true);
    }

    protected AppUser createUser(
        String email,
        String rawPassword,
        boolean verified,
        String... roleNames
    ) {
        Set<Role> roles = Arrays.stream(roleNames)
            .map(this::requiredRole)
            .collect(Collectors.toSet());
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setName(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        if (verified) {
            user.setEmailVerifiedAt(Instant.now());
        }
        user.setRoles(roles);
        return appUserRepository.save(user);
    }

    protected Location createLocation(String name) {
        Location location = new Location();
        location.setName(name);
        return locationRepository.save(location);
    }

    protected void addMembership(Location location, AppUser user) {
        LocationUser membership = new LocationUser();
        membership.setId(new LocationUserId(location.getId(), user.getId()));
        membership.setLocation(location);
        membership.setUser(user);
        locationUserRepository.save(membership);
    }

    protected AuthCookies loginAndCaptureCookies(String email, String password) throws Exception {
        String body = """
            {"email":"%s","password":"%s"}
            """.formatted(email, password);

        MvcResult result = mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isNoContent())
            .andReturn();

        Map<String, String> cookies = readSetCookies(result);
        return new AuthCookies(
            cookies.get(AuthCookieNames.ACCESS_COOKIE_NAME),
            cookies.get(AuthCookieNames.REFRESH_COOKIE_NAME)
        );
    }

    protected Cookie[] authCookies(AuthCookies authCookies) {
        return new Cookie[]{
            new Cookie(AuthCookieNames.ACCESS_COOKIE_NAME, authCookies.accessToken()),
            new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, authCookies.refreshToken())
        };
    }

    protected Cookie[] authCookies(String accessToken, String refreshToken) {
        return new Cookie[]{
            new Cookie(AuthCookieNames.ACCESS_COOKIE_NAME, accessToken),
            new Cookie(AuthCookieNames.REFRESH_COOKIE_NAME, refreshToken)
        };
    }

    protected String createExpiredAccessToken(AppUser user, Long sessionId) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles().stream()
            .map(Role::getName)
            .sorted(Comparator.naturalOrder())
            .toList();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.getIssuer())
            .subject(String.valueOf(user.getId()))
            .issuedAt(now.minusSeconds(1800))
            .expiresAt(now.minusSeconds(60))
            .claim("email", user.getEmail())
            .claim("roles", roles);
        if (sessionId != null) {
            claims.claim("sid", sessionId);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    protected Map<String, String> readSetCookies(MvcResult result) {
        Map<String, String> cookies = new HashMap<>();
        for (String headerValue : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
            List<HttpCookie> parsedCookies = HttpCookie.parse(headerValue);
            if (parsedCookies.isEmpty()) {
                continue;
            }
            HttpCookie cookie = parsedCookies.getFirst();
            cookies.put(cookie.getName(), cookie.getValue());
        }
        return cookies;
    }

    private void resetDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        List<String> tables = List.of(
            "location_invite",
            "location_graph",
            "location_user",
            "graph",
            "location",
            "auth_session",
            "email_verification_token",
            "password_reset_token",
            "user_role",
            "app_user",
            "role"
        );
        for (String table : tables) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    private void seedRoles() {
        for (String roleName : List.of("admin", "partner", "client")) {
            Role role = new Role();
            role.setName(roleName);
            roleRepository.save(role);
        }
    }

    private Role requiredRole(String name) {
        return roleRepository.findByName(name)
            .orElseThrow(() -> new IllegalStateException("Missing seeded role " + name));
    }

    protected record AuthCookies(String accessToken, String refreshToken) {
    }
}
