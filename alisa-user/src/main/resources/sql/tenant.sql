-- Apply after oauth.sql. Existing rows stay in legacy single-tenant mode (empty tenant_id).
ALTER TABLE alisa_oauth_application ADD tenant_id varchar(64) NOT NULL DEFAULT '';
ALTER TABLE alisa_oauth_application ADD approval varchar(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE alisa_oauth_application ADD requested_scopes varchar(2000) NOT NULL DEFAULT '';
CREATE TABLE alisa_tenant (
 id varchar(64) PRIMARY KEY, name varchar(100) NOT NULL, enabled boolean NOT NULL DEFAULT true, token_version varchar(64) NOT NULL,
 created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE alisa_tenant_member (
 tenant_id varchar(64) NOT NULL, user_id varchar(100) NOT NULL, role varchar(20) NOT NULL, token_version varchar(64) NOT NULL,
 PRIMARY KEY(tenant_id,user_id), FOREIGN KEY(tenant_id) REFERENCES alisa_tenant(id)
);
CREATE TABLE alisa_tenant_invitation (
 id varchar(64) PRIMARY KEY, tenant_id varchar(64) NOT NULL, user_id varchar(100) NOT NULL,
 role varchar(20) NOT NULL, token_hash varchar(64) NOT NULL, expires_at timestamp NOT NULL,
 FOREIGN KEY(tenant_id) REFERENCES alisa_tenant(id)
);
CREATE TABLE alisa_tenant_permission (
 tenant_id varchar(64) NOT NULL, application_id varchar(64) NOT NULL, subject_type varchar(20) NOT NULL,
 subject_id varchar(100) NOT NULL, resource_type varchar(64) NOT NULL, resource_id varchar(191) NOT NULL,
 action varchar(64) NOT NULL,
 PRIMARY KEY(tenant_id,application_id,subject_type,subject_id,resource_type,resource_id,action),
 FOREIGN KEY(tenant_id) REFERENCES alisa_tenant(id)
);
CREATE TABLE alisa_tenant_audit (
 id varchar(64) PRIMARY KEY, tenant_id varchar(64) NOT NULL, actor_id varchar(100) NOT NULL,
 event_type varchar(64) NOT NULL, object_id varchar(100) NOT NULL, details varchar(2000) NOT NULL,
 created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(tenant_id) REFERENCES alisa_tenant(id)
);
CREATE INDEX idx_tenant_audit_time ON alisa_tenant_audit(tenant_id,created_at,id);
CREATE INDEX idx_tenant_member_user ON alisa_tenant_member(user_id,tenant_id);
CREATE INDEX idx_oauth_tenant ON alisa_oauth_application(tenant_id,kind,approval);
