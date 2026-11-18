/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 */

package com.kismetsoftware.politigif.signin;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TokensAndCookieTest {

    @Test
    void linkTokensAreRandomUuids() {
        String a = Tokens.newLinkToken();

        assertThat(Tokens.isLinkToken(a)).isTrue();
        assertThat(a).isNotEqualTo(Tokens.newLinkToken());
        assertThat(Tokens.isLinkToken("not-a-uuid")).isFalse();
        assertThat(Tokens.isLinkToken(null)).isFalse();
    }

    @Test
    void noncesAreFortyThreeUrlSafeCharacters() {
        String nonce = Tokens.newNonce();

        assertThat(nonce).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(Tokens.isNonce(nonce)).isTrue();
        assertThat(Tokens.isNonce(nonce + "x")).isFalse();
        assertThat(Tokens.isNonce(null)).isFalse();
    }

    @Test
    void hashesCompareInConstantTimeAndNullIsNeverEqual() {
        assertThat(Tokens.hashesEqual(Tokens.sha256("a"), Tokens.sha256("a"))).isTrue();
        assertThat(Tokens.hashesEqual(Tokens.sha256("a"), Tokens.sha256("b"))).isFalse();
        assertThat(Tokens.hashesEqual(null, Tokens.sha256("a"))).isFalse();
        assertThat(Tokens.sha256("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    // ---- SigninNonceCookie ----

    @Test
    void ensureMintsANonceAndSetsAHardenedCookie() {
        SigninNonceCookie cookie = new SigninNonceCookie(true, Duration.ofMinutes(15));
        MockHttpServletResponse response = new MockHttpServletResponse();

        String nonce = cookie.ensure(new MockHttpServletRequest(), response);

        String header = response.getHeader("Set-Cookie");
        assertThat(Tokens.isNonce(nonce)).isTrue();
        assertThat(header).startsWith(SigninNonceCookie.NAME + "=" + nonce)
                .contains("HttpOnly").contains("Secure").contains("SameSite=Lax")
                .contains("Path=/signin").contains("Max-Age=1200");
    }

    @Test
    void ensureReusesTheBrowsersNonce() {
        SigninNonceCookie cookie = new SigninNonceCookie(true, Duration.ofMinutes(15));
        String existing = Tokens.newNonce();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SigninNonceCookie.NAME, existing));

        assertThat(cookie.ensure(request, new MockHttpServletResponse())).isEqualTo(existing);
    }

    @Test
    void secureCanBeTurnedOffForLocalHttp() {
        SigninNonceCookie cookie = new SigninNonceCookie(false, Duration.ofMinutes(15));
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookie.ensure(new MockHttpServletRequest(), response);

        assertThat(response.getHeader("Set-Cookie")).doesNotContain("Secure");
    }

    @Test
    void readIgnoresAMalformedCookie() {
        SigninNonceCookie cookie = new SigninNonceCookie(true, Duration.ofMinutes(15));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SigninNonceCookie.NAME, "tampered"));

        assertThat(cookie.read(request)).isNull();
        assertThat(cookie.read(new MockHttpServletRequest())).isNull();
    }

    // ---- AdminEmails ----

    @Test
    void adminsAreNamedByConfigCaseInsensitively() {
        AdminEmails admins = new AdminEmails(" Boss@Politigif.com , ,other@x.org");

        assertThat(admins.isAdmin("boss@politigif.com")).isTrue();
        assertThat(admins.isAdmin("OTHER@x.org")).isTrue();
        assertThat(admins.isAdmin("pat@example.com")).isFalse();
        assertThat(new AdminEmails("").isAdmin("")).isFalse();
    }
}
