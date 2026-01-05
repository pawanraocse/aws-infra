package com.learning.common.infra.openfga;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OpenFgaProperties configuration.
 */
class OpenFgaPropertiesTest {

    @Test
    void defaultValues_shouldBeDisabled() {
        OpenFgaProperties properties = new OpenFgaProperties();

        assertFalse(properties.isEnabled(), "OpenFGA should be disabled by default");
        assertEquals("http://localhost:8090", properties.getApiUrl());
        assertNull(properties.getStoreId());
        assertNull(properties.getAuthorizationModelId());
        assertEquals(5000, properties.getConnectTimeoutMs());
        assertEquals(5000, properties.getReadTimeoutMs());
    }

    @Test
    void setEnabled_shouldUpdateValue() {
        OpenFgaProperties properties = new OpenFgaProperties();

        properties.setEnabled(true);

        assertTrue(properties.isEnabled());
    }

    @Test
    void customValues_shouldBeConfigurable() {
        OpenFgaProperties properties = new OpenFgaProperties();

        properties.setApiUrl("http://openfga:8080");
        properties.setStoreId("store-123");
        properties.setAuthorizationModelId("model-456");
        properties.setConnectTimeoutMs(10000);
        properties.setReadTimeoutMs(15000);

        assertEquals("http://openfga:8080", properties.getApiUrl());
        assertEquals("store-123", properties.getStoreId());
        assertEquals("model-456", properties.getAuthorizationModelId());
        assertEquals(10000, properties.getConnectTimeoutMs());
        assertEquals(15000, properties.getReadTimeoutMs());
    }
}
