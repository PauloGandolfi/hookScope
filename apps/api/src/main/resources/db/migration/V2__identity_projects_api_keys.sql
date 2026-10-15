CREATE TABLE hookscope.app_user (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL,
    email varchar(320) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT app_user_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT app_user_email_normalized CHECK (email = lower(btrim(email)))
);

CREATE TABLE hookscope.project (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES hookscope.app_user(id),
    name varchar(120) NOT NULL,
    description varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT project_name_not_blank CHECK (length(btrim(name)) > 0)
);
CREATE INDEX project_owner_created_idx ON hookscope.project(owner_id, created_at DESC, id);

CREATE TABLE hookscope.api_key (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES hookscope.project(id),
    name varchar(120) NOT NULL,
    key_prefix varchar(32) NOT NULL UNIQUE,
    key_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz,
    revoked_at timestamptz,
    last_used_at timestamptz,
    CONSTRAINT api_key_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT api_key_expiry_after_creation CHECK (expires_at IS NULL OR expires_at > created_at)
);
CREATE INDEX api_key_project_created_idx ON hookscope.api_key(project_id, created_at DESC, id);
