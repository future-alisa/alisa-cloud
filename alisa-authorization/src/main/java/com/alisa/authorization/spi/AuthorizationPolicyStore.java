package com.alisa.authorization.spi;

import com.alisa.authorization.model.AuthorizationRequest;

public interface AuthorizationPolicyStore extends PolicyAdministration {
    boolean isGranted(AuthorizationRequest request);
}
