package com.alisa.user.entity;

import lombok.Data;

@Data
public class ExternalIdentityLink {
    private String providerId;
    private String issuer;
    private String subject;
    private String userId;
}
