/*
 * Copyright 2026 Nil MALHOMME (malhomme.nil+oss@icloud.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.ktestify.azureblob.config;

import static org.junit.jupiter.api.Assertions.*;

import com.typesafe.config.ConfigFactory;
import io.github.ktestify.config.KtestifyConfig;
import org.junit.jupiter.api.*;

/**
 * Unit tests for {@link AzureBlobConfig} — HOCON parsing, credential detection, and timeout defaults.
 */
@DisplayName("AzureBlobConfig")
class AzureBlobConfigTest {

    @BeforeEach
    void reset() {
        KtestifyConfig.reset();
    }

    @AfterEach
    void tearDown() {
        KtestifyConfig.reset();
    }

    // =========================================================================
    // Defaults (from reference.conf)
    // =========================================================================

    @Nested
    @DisplayName("default values")
    class DefaultValuesTests {

        @Test
        @DisplayName("loads successfully from reference.conf defaults")
        void loadsFromDefaults() {
            AzureBlobConfig cfg = AzureBlobConfig.from(ConfigFactory.empty());
            assertNotNull(cfg);
        }

        @Test
        @DisplayName("connection-string is blank by default")
        void connectionStringBlankByDefault() {
            AzureBlobConfig cfg = AzureBlobConfig.from(ConfigFactory.empty());
            assertFalse(cfg.hasConnectionString());
        }

        @Test
        @DisplayName("account-key auth not set by default")
        void accountKeyAuthNotSetByDefault() {
            AzureBlobConfig cfg = AzureBlobConfig.from(ConfigFactory.empty());
            assertFalse(cfg.hasAccountKeyAuth());
        }

        @Test
        @DisplayName("sas-token not set by default")
        void sasTokenNotSetByDefault() {
            AzureBlobConfig cfg = AzureBlobConfig.from(ConfigFactory.empty());
            assertFalse(cfg.hasSasToken());
        }

        @Test
        @DisplayName("read-timeout defaults to 30 000 ms")
        void readTimeoutDefaultIs30s() {
            AzureBlobConfig cfg = AzureBlobConfig.from(ConfigFactory.empty());
            assertEquals(30_000L, cfg.getReadTimeoutMs());
        }

        @Test
        @DisplayName("poll-interval defaults to 500 ms")
        void pollIntervalDefaultIs500ms() {
            AzureBlobConfig cfg = AzureBlobConfig.from(ConfigFactory.empty());
            assertEquals(500L, cfg.getPollIntervalMs());
        }
    }

    // =========================================================================
    // Credential detection
    // =========================================================================

    @Nested
    @DisplayName("credential detection")
    class CredentialDetectionTests {

        @Test
        @DisplayName("hasConnectionString() true when connection-string is set")
        void hasConnectionStringWhenSet() {
            AzureBlobConfig cfg = AzureBlobConfig.from(ConfigFactory.parseString(
                    "ktestify.plugins.azure-blob.connection-string = \"DefaultEndpointsProtocol=https;AccountName=x;AccountKey=dGVzdA==;EndpointSuffix=core.windows.net\""));
            assertTrue(cfg.hasConnectionString());
        }

        @Test
        @DisplayName("hasAccountKeyAuth() true when both account-name and account-key are set")
        void hasAccountKeyAuthWhenBothSet() {
            AzureBlobConfig cfg = AzureBlobConfig.from(ConfigFactory.parseString(
                    "ktestify.plugins.azure-blob { account-name = \"myaccount\", account-key = \"dGVzdA==\"  }"));
            assertTrue(cfg.hasAccountKeyAuth());
        }

        @Test
        @DisplayName("hasAccountKeyAuth() false when only account-name is set")
        void hasAccountKeyAuthFalseWhenOnlyName() {
            AzureBlobConfig cfg = AzureBlobConfig.from(
                    ConfigFactory.parseString("ktestify.plugins.azure-blob.account-name = myaccount"));
            assertFalse(cfg.hasAccountKeyAuth());
        }

        @Test
        @DisplayName("hasSasToken() true when sas-token is set")
        void hasSasTokenWhenSet() {
            AzureBlobConfig cfg = AzureBlobConfig.from(
                    ConfigFactory.parseString("ktestify.plugins.azure-blob.sas-token = \"mysastoken\""));
            assertTrue(cfg.hasSasToken());
        }

        @Test
        @DisplayName("hasSasToken() false when sas-token is blank")
        void hasSasTokenFalseWhenBlank() {
            AzureBlobConfig cfg = AzureBlobConfig.from(ConfigFactory.empty());
            assertFalse(cfg.hasSasToken());
        }
    }

    // =========================================================================
    // Custom timeout overrides
    // =========================================================================

    @Nested
    @DisplayName("timeout overrides")
    class TimeoutOverridesTests {

        @Test
        @DisplayName("read-timeout can be overridden")
        void readTimeoutOverridable() {
            AzureBlobConfig cfg = AzureBlobConfig.from(
                    ConfigFactory.parseString("ktestify.plugins.azure-blob.read-timeout = 60s"));
            assertEquals(60_000L, cfg.getReadTimeoutMs());
        }

        @Test
        @DisplayName("poll-interval can be overridden")
        void pollIntervalOverridable() {
            AzureBlobConfig cfg = AzureBlobConfig.from(
                    ConfigFactory.parseString("ktestify.plugins.azure-blob.poll-interval = 1s"));
            assertEquals(1_000L, cfg.getPollIntervalMs());
        }
    }
}



