package io.casehub.ops.app.persistence;

import io.casehub.ops.app.model.CveRecord;
import io.casehub.ops.app.model.CveSeverity;
import io.casehub.ops.app.model.CveStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JpaCveStoreTest {

    @Test
    void recordFieldsPreserved() {
        var record = new CveRecord(
                "CVE-2026-1234", CveSeverity.HIGH, "nginx:1.24",
                List.of("gateway", "proxy"), "nginx:1.25",
                CveStatus.DETECTED, UUID.randomUUID(), "tenant-1", Instant.now());

        assertThat(record.cveId()).isEqualTo("CVE-2026-1234");
        assertThat(record.severity()).isEqualTo(CveSeverity.HIGH);
        assertThat(record.affectedImage()).isEqualTo("nginx:1.24");
        assertThat(record.affectedServices()).containsExactly("gateway", "proxy");
        assertThat(record.fixedInTag()).isEqualTo("nginx:1.25");
        assertThat(record.status()).isEqualTo(CveStatus.DETECTED);
    }

    @Test
    void recordWithNullFixedInTag() {
        var record = new CveRecord(
                "CVE-2026-5678", CveSeverity.CRITICAL, "openssl:3.0",
                List.of("auth-service"), null,
                CveStatus.DETECTED, UUID.randomUUID(), "tenant-1", Instant.now());

        assertThat(record.fixedInTag()).isNull();
    }

    @Test
    void recordWithEmptyAffectedServices() {
        var record = new CveRecord(
                "CVE-2026-9999", CveSeverity.LOW, "busybox:1.36",
                List.of(), null,
                CveStatus.DETECTED, UUID.randomUUID(), "tenant-1", Instant.now());

        assertThat(record.affectedServices()).isEmpty();
    }

    @Test
    void entityMappingRoundTrip() {
        var appId = UUID.randomUUID();
        var now = Instant.now();
        var record = new CveRecord(
                "CVE-2026-1234", CveSeverity.HIGH, "nginx:1.24",
                List.of("gateway", "proxy"), "nginx:1.25",
                CveStatus.DETECTED, appId, "tenant-1", now);

        var entity = JpaCveStore.toEntity(record);

        assertThat(entity.applicationId).isEqualTo(appId);
        assertThat(entity.cveId).isEqualTo("CVE-2026-1234");
        assertThat(entity.severity).isEqualTo(CveSeverity.HIGH);
        assertThat(entity.affectedImage).isEqualTo("nginx:1.24");
        assertThat(entity.affectedServices).isEqualTo("gateway,proxy");
        assertThat(entity.fixedInTag).isEqualTo("nginx:1.25");
        assertThat(entity.status).isEqualTo(CveStatus.DETECTED);
        assertThat(entity.tenancyId).isEqualTo("tenant-1");
        assertThat(entity.detectedAt).isEqualTo(now);

        var mapped = JpaCveStore.toRecord(entity);
        assertThat(mapped).isEqualTo(record);
    }

    @Test
    void entityMappingNullFixedInTag() {
        var record = new CveRecord(
                "CVE-2026-5678", CveSeverity.CRITICAL, "openssl:3.0",
                List.of("auth"), null,
                CveStatus.ESCALATED, UUID.randomUUID(), "tenant-2", Instant.now());

        var entity = JpaCveStore.toEntity(record);
        assertThat(entity.fixedInTag).isNull();

        var mapped = JpaCveStore.toRecord(entity);
        assertThat(mapped.fixedInTag()).isNull();
    }

    @Test
    void entityMappingEmptyServices() {
        var record = new CveRecord(
                "CVE-2026-0000", CveSeverity.LOW, "busybox:1.36",
                List.of(), null,
                CveStatus.DETECTED, UUID.randomUUID(), "tenant-1", Instant.now());

        var entity = JpaCveStore.toEntity(record);
        assertThat(entity.affectedServices).isEmpty();

        var mapped = JpaCveStore.toRecord(entity);
        assertThat(mapped.affectedServices()).isEmpty();
    }
}
