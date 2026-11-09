package com.alisa.user.oauth;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;

class OAuthBrowserPathsTest {
    @AfterEach void reset() { RequestContextHolder.resetRequestAttributes(); }
    @Test void supportsRootAndPrefixedServletContexts() {
        assertEquals("/oauth/apps", OAuthBrowserPaths.path("/oauth/apps"));
        var request=new MockHttpServletRequest();request.setContextPath("/auth");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertEquals("/auth/oauth/tenants",OAuthBrowserPaths.path("/oauth/tenants"));
        String html=OAuthBrowserPaths.html("<a href='/oauth/tenants'>spaces</a><form action='/oauth2/authorize'></form><a href='?before=cursor'>next</a>");
        assertTrue(html.contains("href='/auth/oauth/tenants'"));
        assertTrue(html.contains("action='/auth/oauth2/authorize'"));
        assertTrue(html.contains("href='?before=cursor'"));
        assertThrows(IllegalArgumentException.class,()->OAuthBrowserPaths.path("//external.example"));
    }
    @Test void rejectsMalformedForwardedContext() {
        var request=new MockHttpServletRequest();request.setContextPath("//external.example");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThrows(IllegalStateException.class,()->OAuthBrowserPaths.path("/oauth/apps"));
    }
}
