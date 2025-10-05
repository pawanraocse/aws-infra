-- V1: Initial schema for simple key-value entries
CREATE TABLE entries (
    id BIGSERIAL PRIMARY KEY,
    meta_key VARCHAR(255) NOT NULL,
    meta_value VARCHAR(255) NOT NULL
);
