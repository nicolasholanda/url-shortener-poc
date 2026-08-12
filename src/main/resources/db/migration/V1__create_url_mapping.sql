CREATE TABLE url_mapping (
    id            BIGINT        NOT NULL,
    short_key     VARCHAR(16)   NOT NULL,
    long_url      VARCHAR(2048) NOT NULL,
    long_url_hash VARCHAR(64)   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    expires_at    TIMESTAMPTZ,
    CONSTRAINT pk_url_mapping PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_url_mapping_short_key ON url_mapping (short_key);

CREATE INDEX ix_url_mapping_long_url_hash ON url_mapping (long_url_hash);

CREATE INDEX ix_url_mapping_created_at ON url_mapping (created_at DESC);

CREATE INDEX ix_url_mapping_expires_at ON url_mapping (expires_at) WHERE expires_at IS NOT NULL;
