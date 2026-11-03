-- Apply manually to the authorization service's database before enabling the module.
-- Binary collation prevents case-insensitive identifiers from granting another identity.
CREATE TABLE IF NOT EXISTS alisa_authz_role (
    application_id VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    PRIMARY KEY (application_id, role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS alisa_authz_grant (
    application_id VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(191) NOT NULL,
    action VARCHAR(64) NOT NULL,
    PRIMARY KEY (application_id, role_code, resource_type, resource_id, action),
    CONSTRAINT fk_authz_grant_role FOREIGN KEY (application_id, role_code)
        REFERENCES alisa_authz_role(application_id, role_code) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS alisa_authz_binding (
    application_id VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(191) NOT NULL,
    PRIMARY KEY (application_id, role_code, subject_type, subject_id),
    INDEX idx_authz_subject (application_id, subject_type, subject_id),
    CONSTRAINT fk_authz_binding_role FOREIGN KEY (application_id, role_code)
        REFERENCES alisa_authz_role(application_id, role_code) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
