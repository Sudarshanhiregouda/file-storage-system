-- File storage table
CREATE TABLE IF NOT EXISTS files (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    owner_name VARCHAR(100) NOT NULL,
    is_deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    deleted_at TIMESTAMP NULL,
    storage_path VARCHAR(255) NULL
);

-- Non-clustered indexes for query optimization
CREATE INDEX IF NOT EXISTS idx_files_file_type ON files (file_type);
CREATE INDEX IF NOT EXISTS idx_files_owner_name ON files (owner_name);
CREATE INDEX IF NOT EXISTS idx_files_is_deleted ON files (is_deleted);

-- Audit log table: tracks every lifecycle event on a file
CREATE TABLE IF NOT EXISTS file_audit_log (
    id           BIGSERIAL PRIMARY KEY,
    file_id      BIGINT NOT NULL,
    action       VARCHAR(50) NOT NULL,
    performed_by VARCHAR(100),
    performed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    details      TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_file_id ON file_audit_log (file_id);
CREATE INDEX IF NOT EXISTS idx_audit_performed_at ON file_audit_log (performed_at);

-- Users table: local + OAuth2 (Google) accounts, RBAC role
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    email         VARCHAR(150) UNIQUE,
    password_hash VARCHAR(255),
    role          VARCHAR(20) NOT NULL DEFAULT 'USER',
    auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
