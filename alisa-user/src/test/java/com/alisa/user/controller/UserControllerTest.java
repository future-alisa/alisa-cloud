package com.alisa.user.controller;

import com.alisa.user.dto.user.UserDto;
import com.alisa.user.dto.ChangePasswordRequest;
import com.alisa.user.service.IdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest {
    IdentityService identity;
    MockMvc mvc;
    @BeforeEach void setup() {
        identity = mock(IdentityService.class);
        mvc = MockMvcBuilders.standaloneSetup(new UserController(identity))
                .setControllerAdvice(new IdentityExceptionHandler()).build();
    }
    @Test void healthAndLoginKeepTheExistingWireFormat() throws Exception {
        mvc.perform(get("/api/v1/user/health")).andExpect(status().isOk()).andExpect(content().string("UP"));
        when(identity.signin(any(UserDto.class))).thenReturn("jwt");
        mvc.perform(post("/api/v1/user/signin").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"pw\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").value("jwt"));
    }
    @Test void resetRoutesBothRemainAvailable() throws Exception {
        when(identity.changePassword(any(ChangePasswordRequest.class))).thenReturn(true);
        when(identity.resetPassword(any(ChangePasswordRequest.class))).thenReturn(true);
        for (String path : new String[]{"change-password", "change-password-email"}) {
            mvc.perform(post("/api/v1/user/" + path).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"alice@example.test\",\"code\":\"123456\",\"newPassword\":\"pw\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        }
    }
    @Test void directIngressCannotReadProfilesUsingOnlyAForgedIdentityHeader() throws Exception {
        when(identity.profile(isNull(), isNull())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录"));
        mvc.perform(get("/api/v1/user/profile").header("X-Authenticated", "true").header("X-User-Header", "forged"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }
    @Test void mailSuccessFormatRemainsCompatibleAndValidationErrorsAreVisible() throws Exception {
        mvc.perform(post("/api/v1/user/send").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@example.test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        doThrow(new IllegalArgumentException("邮箱格式无效")).when(identity).sendResetCode("bad");
        mvc.perform(post("/api/v1/user/send").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"bad\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("邮箱格式无效"));
    }
}
