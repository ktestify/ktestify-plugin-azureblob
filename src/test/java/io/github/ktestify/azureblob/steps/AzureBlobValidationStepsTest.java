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
package io.github.ktestify.azureblob.steps;

import static org.junit.jupiter.api.Assertions.*;

import io.cucumber.datatable.DataTable;
import io.cucumber.datatable.DataTableTypeRegistry;
import io.cucumber.datatable.DataTableTypeRegistryTableConverter;
import io.github.ktestify.azureblob.entities.KtestifyBlobContainer;
import io.github.ktestify.config.KtestifyConfig;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.*;

/**
 * Unit tests for {@link AzureBlobValidationSteps}.
 *
 * <p>These tests cover parameter validation and container resolution without connecting to Azure.
 * End-to-end validation behaviour is covered in {@code AzureBlobValidationServiceIT}.
 */
@DisplayName("AzureBlobValidationSteps")
class AzureBlobValidationStepsTest {

    private SharedAzureBlobResources shared;
    private AzureBlobValidationSteps steps;

    @BeforeEach
    void setUp() {
        KtestifyConfig.reset();
        shared = new SharedAzureBlobResources();
        steps = new AzureBlobValidationSteps(shared);
    }

    @AfterEach
    void tearDown() {
        KtestifyConfig.reset();
    }

    // =========================================================================
    // thenExpectedBlobContentFromFile
    // =========================================================================

    @Nested
    @DisplayName("thenExpectedBlobContentFromFile() — parameter validation")
    class ThenExpectedBlobContentFromFileTests {

        @Test
        @DisplayName("throws IllegalArgumentException when containerAlias column is blank")
        void throwsWhenContainerAliasBlank() {
            DataTable dt = buildDataTable(
                    List.of("containerAlias", "blobName", "file"),
                    List.of("", "output.json", "expected.json"));

            assertThrows(IllegalArgumentException.class, () -> steps.thenExpectedBlobContentFromFile(dt));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when containerAlias column is absent")
        void throwsWhenContainerAliasAbsent() {
            DataTable dt = buildDataTable(
                    List.of("blobName", "file"),
                    List.of("output.json", "expected.json"));

            assertThrows(IllegalArgumentException.class, () -> steps.thenExpectedBlobContentFromFile(dt));
        }

        @Test
        @DisplayName("throws IllegalStateException when container is not registered")
        void throwsWhenContainerNotRegistered() {
            DataTable dt = buildDataTable(
                    List.of("containerAlias", "blobName", "file"),
                    List.of("unknown", "output.json", "expected.json"));

            assertThrows(IllegalStateException.class, () -> steps.thenExpectedBlobContentFromFile(dt));
        }
    }

    // =========================================================================
    // thenExpectedBlobXmlContentFromFile
    // =========================================================================

    @Nested
    @DisplayName("thenExpectedBlobXmlContentFromFile() — parameter validation")
    class ThenExpectedBlobXmlContentFromFileTests {

        @Test
        @DisplayName("throws IllegalArgumentException when containerAlias column is blank")
        void throwsWhenContainerAliasBlank() {
            DataTable dt = buildDataTable(
                    List.of("containerAlias", "blobName", "file"),
                    List.of("", "order.xml", "expected.xml"));

            assertThrows(IllegalArgumentException.class, () -> steps.thenExpectedBlobXmlContentFromFile(dt));
        }

        @Test
        @DisplayName("throws IllegalStateException when container is not registered")
        void throwsWhenContainerNotRegistered() {
            DataTable dt = buildDataTable(
                    List.of("containerAlias", "blobName", "file"),
                    List.of("no-such-container", "order.xml", "expected.xml"));

            assertThrows(IllegalStateException.class, () -> steps.thenExpectedBlobXmlContentFromFile(dt));
        }
    }

    // =========================================================================
    // andBlobShouldNotExist
    // =========================================================================

    @Nested
    @DisplayName("andBlobShouldNotExist() — parameter validation")
    class AndBlobShouldNotExistTests {

        @Test
        @DisplayName("throws IllegalArgumentException when containerAlias column is blank")
        void throwsWhenContainerAliasBlank() {
            DataTable dt = buildDataTable(
                    List.of("containerAlias", "blobName"),
                    List.of("", "output.json"));

            assertThrows(IllegalArgumentException.class, () -> steps.andBlobShouldNotExist(dt));
        }

        @Test
        @DisplayName("throws IllegalStateException when container is not registered")
        void throwsWhenContainerNotRegistered() {
            DataTable dt = buildDataTable(
                    List.of("containerAlias", "blobName"),
                    List.of("ghost-container", "output.json"));

            assertThrows(IllegalStateException.class, () -> steps.andBlobShouldNotExist(dt));
        }
    }

    // =========================================================================
    // andBlobShouldExist
    // =========================================================================

    @Nested
    @DisplayName("andBlobShouldExist() — parameter validation")
    class AndBlobShouldExistTests {

        @Test
        @DisplayName("throws IllegalArgumentException when containerAlias column is blank")
        void throwsWhenContainerAliasBlank() {
            DataTable dt = buildDataTable(
                    List.of("containerAlias", "blobName"),
                    List.of("", "output.json"));

            assertThrows(IllegalArgumentException.class, () -> steps.andBlobShouldExist(dt));
        }

        @Test
        @DisplayName("throws IllegalStateException when container is not registered")
        void throwsWhenContainerNotRegistered() {
            DataTable dt = buildDataTable(
                    List.of("containerAlias", "blobName"),
                    List.of("no-container", "output.json"));

            assertThrows(IllegalStateException.class, () -> steps.andBlobShouldExist(dt));
        }
    }

    // =========================================================================
    // Container registered — validation delegated to service (service-level failure is expected)
    // =========================================================================

    @Nested
    @DisplayName("container is registered — service delegation")
    class ServiceDelegationTests {

        @Test
        @DisplayName("thenExpectedBlobContentFromFile delegates to service (throws for missing connection string)")
        void delegatesToServiceForContentValidation() {
            shared.containers.register("real-container", "real-alias", container("real-container"));

            DataTable dt = buildDataTable(
                    List.of("containerAlias", "blobName", "file", "readTimeout"),
                    List.of("real-alias", "output.json", "/any/path.json", "1"));

            assertThrows(RuntimeException.class, () -> steps.thenExpectedBlobContentFromFile(dt));
        }

        @Test
        @DisplayName("andBlobShouldNotExist passes (returns normally) when no credentials — treated as blob-not-found")
        void delegatesToServiceForAbsenceCheck() {
            shared.containers.register("real-container", "real-alias", container("real-container"));

            DataTable dt = buildDataTable(
                    List.of("containerAlias", "blobName", "readTimeout"),
                    List.of("real-alias", "output.json", "1"));

            // validateBlobAbsent() catches ConsumerException (including no-credentials PluginException)
            // and treats it as "blob not found" = expected = no exception thrown.
            assertDoesNotThrow(() -> steps.andBlobShouldNotExist(dt));
        }

        @Test
        @DisplayName("andBlobShouldExist delegates to service (throws for missing connection string)")
        void delegatesToServiceForExistenceCheck() {
            shared.containers.register("real-container", "real-alias", container("real-container"));

            DataTable dt = buildDataTable(
                    List.of("containerAlias", "blobName", "readTimeout"),
                    List.of("real-alias", "output.json", "1"));

            assertThrows(RuntimeException.class, () -> steps.andBlobShouldExist(dt));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static KtestifyBlobContainer container(String name) {
        return KtestifyBlobContainer.builder().containerName(name).build();
    }

    private static DataTable buildDataTable(List<String> headers, List<String> values) {
        DataTableTypeRegistry registry = new DataTableTypeRegistry(Locale.ENGLISH);
        DataTableTypeRegistryTableConverter converter = new DataTableTypeRegistryTableConverter(registry);
        return DataTable.create(List.of(headers, values), converter);
    }
}


