package com.learning.platformservice.sso.service;

import com.learning.platformservice.config.CognitoProperties;
import com.learning.platformservice.sso.dto.OidcConfigRequest;
import com.learning.platformservice.sso.dto.SamlConfigRequest;
import com.learning.platformservice.sso.dto.SsoConfigDto;
import com.learning.platformservice.sso.dto.SsoTestResult;
import com.learning.platformservice.tenant.entity.IdpType;
import com.learning.platformservice.tenant.entity.Tenant;
import com.learning.platformservice.tenant.repo.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateIdentityProviderRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateIdentityProviderResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeleteIdentityProviderRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeleteIdentityProviderResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DescribeIdentityProviderRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DescribeIdentityProviderResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.IdentityProviderType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UpdateIdentityProviderRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UpdateIdentityProviderResponse;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SsoConfigurationServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class SsoConfigurationServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private CognitoIdentityProviderClient cognitoClient;

    @Mock
    private CognitoProperties cognitoProperties;

    @InjectMocks
    private SsoConfigurationServiceImpl ssoConfigurationService;

    @Captor
    private ArgumentCaptor<Tenant> tenantCaptor;

    @Captor
    private ArgumentCaptor<CreateIdentityProviderRequest> createRequestCaptor;

    private static final String TENANT_ID = "test-tenant-123";
    private static final String USER_POOL_ID = "us-east-1_testPool";

    @BeforeEach
    void setUp() {
        lenient().when(cognitoProperties.getUserPoolId()).thenReturn(USER_POOL_ID);
        lenient().when(cognitoProperties.getRegion()).thenReturn("us-east-1");
    }

    @Nested
    @DisplayName("getConfiguration")
    class GetConfiguration {

        @Test
        @DisplayName("should return SSO config when tenant has IdP configured")
        void shouldReturnConfigWhenExists() {
            // Given
            Tenant tenant = createTenantWithSso();
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            // When
            Optional<SsoConfigDto> result = ssoConfigurationService.getConfiguration(TENANT_ID);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().isSsoEnabled()).isTrue();
            assertThat(result.get().getIdpType()).isEqualTo(IdpType.OKTA);
        }

        @Test
        @DisplayName("should return empty when tenant has no SSO configured")
        void shouldReturnEmptyWhenNoSso() {
            // Given
            Tenant tenant = createTenantWithoutSso();
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            // When
            Optional<SsoConfigDto> result = ssoConfigurationService.getConfiguration(TENANT_ID);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty when tenant not found")
        void shouldReturnEmptyWhenTenantNotFound() {
            // Given
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

            // When
            Optional<SsoConfigDto> result = ssoConfigurationService.getConfiguration(TENANT_ID);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("saveSamlConfiguration")
    class SaveSamlConfiguration {

        @Test
        @DisplayName("should create new SAML provider in Cognito")
        void shouldCreateSamlProvider() {
            // Given
            Tenant tenant = createTenantWithoutSso();
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

            // Simulate provider doesn't exist
            when(cognitoClient.describeIdentityProvider(any(DescribeIdentityProviderRequest.class)))
                    .thenThrow(ResourceNotFoundException.builder().message("Not found").build());
            when(cognitoClient.createIdentityProvider(any(CreateIdentityProviderRequest.class)))
                    .thenReturn(CreateIdentityProviderResponse.builder().build());

            SamlConfigRequest request = new SamlConfigRequest(
                    IdpType.OKTA,
                    "Okta SSO",
                    "https://dev-xxx.okta.com/app/xxx/sso/saml/metadata",
                    null, null, null, null, null,
                    true, "user");

            // When
            SsoConfigDto result = ssoConfigurationService.saveSamlConfiguration(TENANT_ID, request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isSsoEnabled()).isTrue();
            assertThat(result.getIdpType()).isEqualTo(IdpType.OKTA);

            verify(cognitoClient).createIdentityProvider(createRequestCaptor.capture());
            CreateIdentityProviderRequest capturedRequest = createRequestCaptor.getValue();
            assertThat(capturedRequest.userPoolId()).isEqualTo(USER_POOL_ID);
            assertThat(capturedRequest.providerTypeAsString()).isEqualTo("SAML");
        }

        @Test
        @DisplayName("should update existing SAML provider in Cognito")
        void shouldUpdateExistingSamlProvider() {
            // Given
            Tenant tenant = createTenantWithSso();
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

            // Simulate provider exists
            when(cognitoClient.describeIdentityProvider(any(DescribeIdentityProviderRequest.class)))
                    .thenReturn(DescribeIdentityProviderResponse.builder()
                            .identityProvider(IdentityProviderType.builder()
                                    .providerName("OKTA_testtenant123")
                                    .build())
                            .build());
            when(cognitoClient.updateIdentityProvider(any(UpdateIdentityProviderRequest.class)))
                    .thenReturn(UpdateIdentityProviderResponse.builder().build());

            SamlConfigRequest request = new SamlConfigRequest(
                    IdpType.OKTA, "Updated Okta",
                    "https://dev-xxx.okta.com/app/xxx/sso/saml/metadata",
                    null, null, null, null, null,
                    true, "admin");

            // When
            SsoConfigDto result = ssoConfigurationService.saveSamlConfiguration(TENANT_ID, request);

            // Then
            verify(cognitoClient).updateIdentityProvider(any(UpdateIdentityProviderRequest.class));
            verify(cognitoClient, never()).createIdentityProvider(any(CreateIdentityProviderRequest.class));
        }
    }

    @Nested
    @DisplayName("saveOidcConfiguration")
    class SaveOidcConfiguration {

        @Test
        @DisplayName("should create Google provider with correct issuer")
        void shouldCreateGoogleProvider() {
            // Given
            Tenant tenant = createTenantWithoutSso();
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cognitoClient.describeIdentityProvider(any(DescribeIdentityProviderRequest.class)))
                    .thenThrow(ResourceNotFoundException.builder().message("Not found").build());
            when(cognitoClient.createIdentityProvider(any(CreateIdentityProviderRequest.class)))
                    .thenReturn(CreateIdentityProviderResponse.builder().build());

            OidcConfigRequest request = new OidcConfigRequest(
                    IdpType.GOOGLE, "Google SSO",
                    "google-client-id", "google-client-secret",
                    null, null, null, true, "user");

            // When
            SsoConfigDto result = ssoConfigurationService.saveOidcConfiguration(TENANT_ID, request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getIdpType()).isEqualTo(IdpType.GOOGLE);

            verify(cognitoClient).createIdentityProvider(createRequestCaptor.capture());
            CreateIdentityProviderRequest capturedRequest = createRequestCaptor.getValue();
            assertThat(capturedRequest.providerTypeAsString()).isEqualTo("Google");
        }
    }

    @Nested
    @DisplayName("toggleSso")
    class ToggleSso {

        @Test
        @DisplayName("should enable SSO")
        void shouldEnableSso() {
            // Given
            Tenant tenant = createTenantWithSso();
            tenant.setSsoEnabled(false);
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            SsoConfigDto result = ssoConfigurationService.toggleSso(TENANT_ID, true);

            // Then
            verify(tenantRepository).save(tenantCaptor.capture());
            assertThat(tenantCaptor.getValue().getSsoEnabled()).isTrue();
        }

        @Test
        @DisplayName("should disable SSO")
        void shouldDisableSso() {
            // Given
            Tenant tenant = createTenantWithSso();
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            SsoConfigDto result = ssoConfigurationService.toggleSso(TENANT_ID, false);

            // Then
            verify(tenantRepository).save(tenantCaptor.capture());
            assertThat(tenantCaptor.getValue().getSsoEnabled()).isFalse();
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            // Given
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> ssoConfigurationService.toggleSso(TENANT_ID, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Tenant not found");
        }
    }

    @Nested
    @DisplayName("deleteConfiguration")
    class DeleteConfiguration {

        @Test
        @DisplayName("should delete Cognito provider and clear tenant SSO fields")
        void shouldDeleteConfiguration() {
            // Given
            Tenant tenant = createTenantWithSso();
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cognitoClient.deleteIdentityProvider(any(DeleteIdentityProviderRequest.class)))
                    .thenReturn(DeleteIdentityProviderResponse.builder().build());

            // When
            ssoConfigurationService.deleteConfiguration(TENANT_ID);

            // Then
            verify(cognitoClient).deleteIdentityProvider(any(DeleteIdentityProviderRequest.class));
            verify(tenantRepository).save(tenantCaptor.capture());

            Tenant savedTenant = tenantCaptor.getValue();
            assertThat(savedTenant.getSsoEnabled()).isFalse();
            assertThat(savedTenant.getIdpType()).isNull();
            assertThat(savedTenant.getIdpConfigJson()).isNull();
        }
    }

    @Nested
    @DisplayName("testConnection")
    class TestConnection {

        @Test
        @DisplayName("should return success when provider exists")
        void shouldReturnSuccessWhenProviderExists() {
            // Given
            Tenant tenant = createTenantWithSso();
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            when(cognitoClient.describeIdentityProvider(any(DescribeIdentityProviderRequest.class)))
                    .thenReturn(DescribeIdentityProviderResponse.builder()
                            .identityProvider(IdentityProviderType.builder()
                                    .providerName("OKTA_testtenant123")
                                    .providerDetails(Map.of("IDPEntityId", "https://okta.example.com"))
                                    .attributeMapping(Map.of("email", "email"))
                                    .build())
                            .build());

            // When
            SsoTestResult result = ssoConfigurationService.testConnection(TENANT_ID);

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("Connection successful");
        }

        @Test
        @DisplayName("should return failure when provider not found")
        void shouldReturnFailureWhenProviderNotFound() {
            // Given
            Tenant tenant = createTenantWithSso();
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
            when(cognitoClient.describeIdentityProvider(any(DescribeIdentityProviderRequest.class)))
                    .thenThrow(ResourceNotFoundException.builder().message("Not found").build());

            // When
            SsoTestResult result = ssoConfigurationService.testConnection(TENANT_ID);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("not found");
        }

        @Test
        @DisplayName("should return failure when SSO not configured")
        void shouldReturnFailureWhenNotConfigured() {
            // Given
            Tenant tenant = createTenantWithoutSso();
            when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));

            // When
            SsoTestResult result = ssoConfigurationService.testConnection(TENANT_ID);

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("not configured");
        }
    }

    // ========== Helper Methods ==========

    private Tenant createTenantWithSso() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setName("Test Tenant");
        tenant.setSsoEnabled(true);
        tenant.setIdpType(IdpType.OKTA);
        tenant.setIdpMetadataUrl("https://dev-xxx.okta.com/app/xxx/sso/saml/metadata");

        Map<String, Object> config = new HashMap<>();
        config.put("cognitoProviderName", "OKTA_testtenant123");
        config.put("providerName", "Okta SSO");
        config.put("jitProvisioningEnabled", true);
        config.put("defaultRole", "user");
        tenant.setIdpConfigJson(config);

        tenant.setCreatedAt(OffsetDateTime.now());
        tenant.setUpdatedAt(OffsetDateTime.now());
        return tenant;
    }

    private Tenant createTenantWithoutSso() {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setName("Test Tenant");
        tenant.setSsoEnabled(false);
        tenant.setCreatedAt(OffsetDateTime.now());
        tenant.setUpdatedAt(OffsetDateTime.now());
        return tenant;
    }
}
