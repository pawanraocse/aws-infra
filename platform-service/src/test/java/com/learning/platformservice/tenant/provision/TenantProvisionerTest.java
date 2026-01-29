package com.learning.platformservice.tenant.provision;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantProvisionerTest {

    DataSource ds = Mockito.mock(DataSource.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    
    private static final String BASE_JDBC_URL = "jdbc:postgresql://localhost:5432/cloudinfra";
    private static final String PERSONAL_SHARED_JDBC_URL = "jdbc:postgresql://localhost:5432/personal_shared";

    @Test
    @DisplayName("SHARED mode returns personal shared JDBC URL")
    void sharedMode_returnsPersonalSharedUrl() {
        TenantProvisioner provisioner = new TenantProvisioner(ds, registry, true, true,
                BASE_JDBC_URL, PERSONAL_SHARED_JDBC_URL);
        String jdbc = provisioner.provisionTenantStorage("personal_user_123", TenantStorageEnum.SHARED);
        assertThat(jdbc).isEqualTo(PERSONAL_SHARED_JDBC_URL);
    }

    @Test
    @DisplayName("DATABASE mode disabled throws flag error before JDBC")
    void databaseMode_disabled() {
        TenantProvisioner provisioner = new TenantProvisioner(ds, registry, false, false,
                BASE_JDBC_URL, PERSONAL_SHARED_JDBC_URL);
        assertThatThrownBy(() -> provisioner.provisionTenantStorage("acme", TenantStorageEnum.DATABASE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATABASE storageMode disabled");
    }

    @Test
    @DisplayName("Database name sanitization applies length and allowed chars")
    void databaseName_sanitization() throws Exception {
        TenantProvisioner provisioner = new TenantProvisioner(ds, registry, true, true,
                BASE_JDBC_URL, PERSONAL_SHARED_JDBC_URL);
        var method = TenantProvisioner.class.getDeclaredMethod("buildDatabaseName", String.class);
        method.setAccessible(true);
        String name = (String) method.invoke(provisioner, "ACME-*INVALID__LONG_NAME_WITH_CHARS@#$%^&*()+");
        assertThat(name).contains("acme_invalid__long_name_with_chars");
        assertThat(name).matches("[a-z0-9_-]+");
        assertThat(name.length()).isLessThanOrEqualTo(63);
    }
}
