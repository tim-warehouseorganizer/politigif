/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.security;

import com.kismetsoftware.politigif.user.User;
import com.kismetsoftware.politigif.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountStatusFilterTest {

    private UserRepository users;
    private SessionSignIn sessionSignIn;
    private AccountStatusFilter filter;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        sessionSignIn = mock(SessionSignIn.class);
        filter = new AccountStatusFilter(users, sessionSignIn);
        user = new User("pat@example.com", Instant.EPOCH);
        user.setId("u1");
        user.setSeedlingHash("$2a$hash");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signedIn() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new PolitigifPrincipal("u1", "pat@example.com", false), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private MockHttpServletResponse run(String path, MockFilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void anonymousRequestsPassWithoutALookup() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        run("/upload", chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(users, never()).findById(any());
    }

    @Test
    void aSignedInAccountWithASeedlingPasses() throws Exception {
        signedIn();
        when(users.findById("u1")).thenReturn(Optional.of(user));
        MockFilterChain chain = new MockFilterChain();

        run("/upload", chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void aResetOrDeletedAccountEndsTheSession() throws Exception {
        signedIn();
        when(users.findById("u1")).thenReturn(Optional.empty());
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run("/upload", chain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/signin?ended");
        assertThat(chain.getRequest()).isNull();
        verify(sessionSignIn).signOut(any());
    }

    @Test
    void anAccountWithoutASeedlingIsHeldAtTheSeedlingPage() throws Exception {
        signedIn();
        user.setSeedlingHash(null);
        when(users.findById("u1")).thenReturn(Optional.of(user));
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run("/upload", chain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/seedling");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void theSeedlingPageItselfStaysReachable() throws Exception {
        signedIn();
        user.setSeedlingHash(null);
        when(users.findById("u1")).thenReturn(Optional.of(user));
        MockFilterChain chain = new MockFilterChain();

        run("/seedling", chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void signInSignOutAndAssetsAreNeverChecked() throws Exception {
        signedIn();
        for (String path : List.of("/signin/abc", "/signout", "/css/site.css", "/error")) {
            MockFilterChain chain = new MockFilterChain();
            run(path, chain);
            assertThat(chain.getRequest()).as(path).isNotNull();
        }
        verify(users, never()).findById(any());
    }
}
