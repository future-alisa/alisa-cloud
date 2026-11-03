package com.alisa.security;
import com.alisa.interceptors.UserInterceptor;
import com.alisa.model.UserContext;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class VerifiedUserInterceptorTest {
    @AfterEach void clear() {UserContext.remove();}
    @Test void identityHeadersCannotOverrideTheVerifiedBearerSubject() throws Exception {
        var access=mock(ResourceAccess.class);var request=new MockHttpServletRequest();
        var forged=new UserContext();forged.setUserId("admin");request.addHeader("X-User-Header",forged.toBase64Json());
        when(access.authenticate(request)).thenReturn(new RequestIdentity("alice","Alice",true,"client","demo",java.util.Set.of("demo:target:read")));
        var interceptor=new UserInterceptor(access);
        assertTrue(interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
        assertEquals("alice",UserContext.get().getUserId());
        interceptor.afterCompletion(request,new MockHttpServletResponse(),new Object(),null);assertNull(UserContext.get());
    }
    @Test void invalidBearerNeverLeavesAUserContext() throws Exception {
        var access=mock(ResourceAccess.class);var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();
        when(access.authenticate(request)).thenThrow(new AccessFailure(401,"Invalid token"));
        assertFalse(new UserInterceptor(access).preHandle(request,response,new Object()));
        assertEquals(401,response.getStatus());assertNull(UserContext.get());
    }
}
