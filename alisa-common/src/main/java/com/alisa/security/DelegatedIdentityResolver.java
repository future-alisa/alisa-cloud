package com.alisa.security;

/** Implemented inside the issuing service; never accepts client-supplied identity claims. */
public interface DelegatedIdentityResolver {
    RequestIdentity resolve(String accessToken);
}
