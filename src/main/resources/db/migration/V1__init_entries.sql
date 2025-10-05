-- V1: Initial schema for entries and entry_metadata
CREATE TABLE entries (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE entry_metadata (
    entry_id UUID NOT NULL,
    meta_key VARCHAR(255) NOT NULL,
    meta_value VARCHAR(255),
    PRIMARY KEY (entry_id, meta_key),
    FOREIGN KEY (entry_id) REFERENCES entries(id) ON DELETE CASCADE
);

