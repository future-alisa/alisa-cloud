-- Apply manually only when enabling external identity providers.
-- Account table and IDs remain unchanged; email is deliberately not an identity key.
CREATE TABLE IF NOT EXISTS tb_external_identity (
    provider_id VARCHAR(64) NOT NULL,
    issuer VARCHAR(255) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    user_id CHAR(32) NOT NULL,
    PRIMARY KEY (provider_id, issuer, subject_id),
    INDEX idx_external_identity_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
