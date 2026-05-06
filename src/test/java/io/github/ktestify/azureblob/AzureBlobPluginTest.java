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
package io.github.ktestify.azureblob;

import static org.junit.jupiter.api.Assertions.*;

import com.typesafe.config.ConfigFactory;
import io.github.ktestify.config.KtestifyConfig;
import io.github.ktestify.plugin.PluginContext;
import org.junit.jupiter.api.*;

/**
 * Unit tests for {@link AzureBlobPlugin} — lifecycle, metadata, and configuration validation.
 *
 * <p>These tests do not require Docker; they only exercise config loading and plugin contract methods.
 */
@DisplayName("AzureBlobPlugin")
class AzureBlobPluginTest {

    private AzureBlobPlugin plugin;
    private PluginContext ctx;

    @BeforeEach
    void setUp() {
        KtestifyConfig.reset();
        plugin = new AzureBlobPlugin();
        ctx = KtestifyConfig::getOrLoad;
    }

    @AfterEach
    void tearDown() {
        KtestifyConfig.reset();
    }

    // =========================================================================
    // Identity / metadata
    // =========================================================================

    @Nested
    @DisplayName("Metadata")
    class MetadataTests {

        @Test
        @DisplayName("getId() returns 'azure-blob'")
        void idIsAzureBlob() {
            assertEquals("azure-blob", plugin.getId());
        }

        @Test
        @DisplayName("getVersion() is non-blank")
        void versionIsNonBlank() {
            assertNotNull(plugin.getVersion());
            assertFalse(plugin.getVersion().isBlank());
        }

        @Test
        @DisplayName("getAuthorName() returns 'Nil MALHOMME'")
        void authorNameIsSet() {
            assertEquals("Nil MALHOMME", plugin.getAuthorName());
        }

        @Test
        @DisplayName("getAuthorEmail() returns the expected email")
        void authorEmailIsSet() {
            assertEquals("malhomme.nil+oss@icloud.com", plugin.getAuthorEmail());
        }

        @Test
        @DisplayName("getGluePackage() returns the steps package")
        void gluePackageIsStepsPackage() {
            assertEquals("io.github.ktestify.azureblob.steps", plugin.getGluePackage());
        }
    }

    // =========================================================================
    // initialize()
    // =========================================================================

    @Nested
    @DisplayName("initialize()")
    class InitializeTests {

        @Test
        @DisplayName("initialize() succeeds when config section is present (no credentials configured = warn only)")
        void initializeSucceedsWithNoCreds() {
            // reference.conf provides the ktestify.plugins.azure-blob section — no exception
            assertDoesNotThrow(() -> plugin.initialize(ctx));
        }

        @Test
        @DisplayName("initialize() succeeds when connection string is set")
        void initializeSucceedsWithConnectionString() {
            KtestifyConfig cfg = KtestifyConfig.load(ConfigFactory.parseString(
                    "ktestify.plugins.azure-blob.connection-string = \"DefaultEndpointsProtocol=https;AccountName=test;AccountKey=dGVzdA==;EndpointSuffix=core.windows.net\""));

            assertDoesNotThrow(() -> plugin.initialize(() -> cfg));
        }

        @Test
        @DisplayName("initialize() succeeds when account-name + account-key are set")
        void initializeSucceedsWithAccountKeyAuth() {
            KtestifyConfig cfg = KtestifyConfig.load(ConfigFactory.parseString(
                    "ktestify.plugins.azure-blob { account-name = \"myaccount\", account-key = \"dGVzdA==\" }"));

            assertDoesNotThrow(() -> plugin.initialize(() -> cfg));
        }
    }

    // =========================================================================
    // shutdown()
    // =========================================================================

    @Nested
    @DisplayName("shutdown()")
    class ShutdownTests {

        @Test
        @DisplayName("shutdown() before initialize() does not throw")
        void shutdownBeforeInitDoesNotThrow() {
            assertDoesNotThrow(plugin::shutdown);
        }

        @Test
        @DisplayName("shutdown() after initialize() does not throw")
        void shutdownAfterInitDoesNotThrow() {
            plugin.initialize(ctx);
            assertDoesNotThrow(plugin::shutdown);
        }
    }
}



