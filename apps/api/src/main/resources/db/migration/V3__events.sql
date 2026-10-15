CREATE TABLE hookscope.event (
    project_id uuid NOT NULL REFERENCES hookscope.project(id),
    event_id uuid NOT NULL,
    event_type varchar(120) NOT NULL,
    status varchar(16) NOT NULL,
    duration_ms integer NOT NULL,
    occurred_at timestamptz NOT NULL,
    ingested_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (project_id, event_id),
    CONSTRAINT event_type_format CHECK (
        event_type ~ '^[a-z][a-z0-9]*([.][a-z0-9]+)*$'
    ),
    CONSTRAINT event_status_supported CHECK (
        status IN ('SUCCESS', 'ERROR', 'TIMEOUT', 'CANCELLED')
    ),
    CONSTRAINT event_duration_range CHECK (
        duration_ms BETWEEN 0 AND 86400000
    ),
    CONSTRAINT event_metadata_object CHECK (
        jsonb_typeof(metadata) = 'object'
    ),
    CONSTRAINT event_metadata_size CHECK (
        octet_length(metadata::text) <= 16384
    )
);

CREATE INDEX event_project_occurred_idx
    ON hookscope.event(project_id, occurred_at DESC, event_id DESC);

CREATE INDEX event_project_status_occurred_idx
    ON hookscope.event(project_id, status, occurred_at DESC, event_id DESC);

CREATE INDEX event_project_type_occurred_idx
    ON hookscope.event(project_id, event_type, occurred_at DESC, event_id DESC);
