CREATE TABLE cve_record (
    cve_id          VARCHAR(64)  NOT NULL,
    severity        VARCHAR(16)  NOT NULL,
    affected_image  VARCHAR(512) NOT NULL,
    affected_services TEXT,
    fixed_in_tag    VARCHAR(256),
    status          VARCHAR(16)  NOT NULL DEFAULT 'DETECTED',
    application_id  UUID         NOT NULL,
    tenancy_id      VARCHAR(128) NOT NULL,
    detected_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (application_id, cve_id)
);

CREATE INDEX idx_cve_record_app ON cve_record(application_id);
CREATE INDEX idx_cve_record_tenancy ON cve_record(tenancy_id);
