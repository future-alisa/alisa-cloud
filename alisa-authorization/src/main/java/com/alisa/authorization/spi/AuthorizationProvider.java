package com.alisa.authorization.spi;

import com.alisa.authorization.model.AuthorizationDecision;
import com.alisa.authorization.model.AuthorizationRequest;

/** Providers own protocol/storage details; the core never branches on vendor names. */
public interface AuthorizationProvider {
    String id();
    AuthorizationDecision decide(AuthorizationRequest request);
}
