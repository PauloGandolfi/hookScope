package com.hookscope.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.hookscope.api.domain.ApiKeyMaterial;
import org.junit.jupiter.api.Test;

class ApiKeyMaterialTest {
    @Test
    void generatedKeysHaveRandomSecretAndVerifiableHash() {
        var first = ApiKeyMaterial.generate();
        var second = ApiKeyMaterial.generate();
        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(first.value()).startsWith(first.prefix() + "_");
        assertThat(ApiKeyMaterial.prefixOf(first.value())).contains(first.prefix());
        assertThat(ApiKeyMaterial.matches(first.value(), first.hash())).isTrue();
        assertThat(ApiKeyMaterial.matches(second.value(), first.hash())).isFalse();
        assertThat(first.hash()).doesNotContain(first.value());
    }
}
