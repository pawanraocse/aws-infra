package com.learning.platformservice.tenant.provision;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.*;

class TenantProvisionerTest {

    DataSource ds = Mockito.mock(DataSource.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    @DisplayName("Schema mode constructs JDBC URL with currentSchema param")
    void schemaMode_urlConstruction() {
        TenantProvisioner provisioner = new TenantProvisioner(ds, registry, false, false, "jdbc:postgresql://localhost:5432/awsinfra");
        String jdbc = provisioner.provisionTenantStorage("Acme_123", TenantStorageEnum.SCHEMA);
        assertThat(jdbc).startsWith("jdbc:postgresql://localhost:5432/awsinfra?currentSchema=");
        assertThat(jdbc).contains("acme_123");
    }

    @Test
    @DisplayName("Database mode disabled throws flag error before JDBC")
    void databaseMode_disabled() {
        TenantProvisioner provisioner = new TenantProvisioner(ds, registry, false, false, "jdbc:postgresql://localhost:5432/awsinfra");
        assertThatThrownBy(() -> provisioner.provisionTenantStorage("acme", TenantStorageEnum.DATABASE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATABASE storageMode disabled");
    }

    @Test
    @DisplayName("Database name sanitization applies length and allowed chars")
    void databaseName_sanitization() throws Exception {
        TenantProvisioner provisioner = new TenantProvisioner(ds, registry, true, true, "jdbc:postgresql://localhost:5432/awsinfra");
        var method = TenantProvisioner.class.getDeclaredMethod("buildDatabaseName", String.class);
        method.setAccessible(true);
        String name = (String) method.invoke(provisioner, "ACME-*INVALID__LONG_NAME_WITH_CHARS@#$%^&*()+");
        assertThat(name).contains("acme-invalid__long_name_with_chars");
        assertThat(name).matches("[a-z0-9_-]+");
        assertThat(name.length()).isLessThanOrEqualTo(63);
    }
}
