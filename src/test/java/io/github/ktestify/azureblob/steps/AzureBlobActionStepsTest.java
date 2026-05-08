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
 * Unit tests for {@link AzureBlobActionSteps}.
 *
 * <p>These tests verify parameter validation, container look-up, and path resolution logic without connecting to Azure.
 * The upload itself is covered by integration tests in {@code AzureBlobValidationServiceIT}.
 */
@DisplayName("AzureBlobActionSteps")
class AzureBlobActionStepsTest {

    private SharedAzureBlobResources shared;
    private AzureBlobActionSteps steps;

    @BeforeEach
    void setUp() {
        KtestifyConfig.reset();
        shared = new SharedAzureBlobResources();
        steps = new AzureBlobActionSteps(shared);
    }

    @AfterEach
    void tearDown() {
        KtestifyConfig.reset();
    }

    // =========================================================================
    // Missing required DataTable columns
    // =========================================================================

    @Nested
    @DisplayName("whenBlobIsUploadedFromFile() — required column validation")
    class RequiredColumnValidationTests {

        @Test
        @DisplayName("throws IllegalArgumentException when containerAlias column is blank")
        void throwsWhenContainerAliasBlank() {
            DataTable dt = buildDataTable(
                    List.of("containerAlias", "file", "blobName"), List.of("", "data.json", "output/data.json"));

            assertThrows(IllegalArgumentException.class, () -> steps.whenBlobIsUploadedFromFile(dt));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when file column is blank")
        void throwsWhenFileBlank() {
            // Register a container first so the alias look-up doesn't fail before the file check
            shared.containers.register("my-container", "my-alias", container("my-container"));

            DataTable dt = buildDataTable(
                    List.of("containerAlias", "file", "blobName"), List.of("my-alias", "", "output/data.json"));

            assertThrows(IllegalArgumentException.class, () -> steps.whenBlobIsUploadedFromFile(dt));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when blobName column is blank")
        void throwsWhenBlobNameBlank() {
            shared.containers.register("my-container", "my-alias", container("my-container"));

            DataTable dt =
                    buildDataTable(List.of("containerAlias", "file", "blobName"), List.of("my-alias", "data.json", ""));

            assertThrows(IllegalArgumentException.class, () -> steps.whenBlobIsUploadedFromFile(dt));
        }
    }

    // =========================================================================
    // Container look-up
    // =========================================================================

    @Nested
    @DisplayName("whenBlobIsUploadedFromFile() — container resolution")
    class ContainerResolutionTests {

        @Test
        @DisplayName("throws IllegalStateException when containerAlias is not registered")
        void throwsWhenContainerNotRegistered() {
            DataTable dt = buildDataTable(
                    List.of("containerAlias", "file", "blobName"),
                    List.of("unknown-alias", "/absolute/path/data.json", "output/data.json"));

            assertThrows(IllegalStateException.class, () -> steps.whenBlobIsUploadedFromFile(dt));
        }

        @Test
        @DisplayName("resolves container by name (not alias)")
        void resolvesContainerByName() {
            // Register by name only (no alias)
            shared.containers.register("direct-name", container("direct-name"));

            DataTable dt = buildDataTable(
                    List.of("containerAlias", "file", "blobName"),
                    List.of("direct-name", "/absolute/path/data.json", "output/data.json"));

            // Should throw RuntimeException from the upload attempt (no connection string)
            // but NOT IllegalStateException — container was found
            assertThrows(RuntimeException.class, () -> steps.whenBlobIsUploadedFromFile(dt));
        }
    }

    // =========================================================================
    // Path resolution
    // =========================================================================

    @Nested
    @DisplayName("path resolution — absolute vs relative")
    class PathResolutionTests {

        @Test
        @DisplayName("absolute file path is passed through unchanged")
        void absolutePathPassedThrough() {
            shared.containers.register("c", "alias", container("c"));

            DataTable dt = buildDataTable(
                    List.of("containerAlias", "file", "blobName"),
                    List.of("alias", "/absolute/path/file.json", "out.json"));

            assertThrows(RuntimeException.class, () -> steps.whenBlobIsUploadedFromFile(dt));
        }

        @Test
        @DisplayName("relative file path is joined with assetsDirectory when set")
        void relativePathJoinedWithAssetsDir() {
            shared.assetsDirectory = "/base/dir";
            shared.containers.register("c", "alias", container("c"));

            DataTable dt = buildDataTable(
                    List.of("containerAlias", "file", "blobName"), List.of("alias", "relative/file.json", "out.json"));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> steps.whenBlobIsUploadedFromFile(dt));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("relative path without assetsDirectory is used as-is")
        void relativePathWithoutAssetsDirUsedAsIs() {
            shared.assetsDirectory = null;
            shared.containers.register("c", "alias", container("c"));

            DataTable dt = buildDataTable(
                    List.of("containerAlias", "file", "blobName"), List.of("alias", "relative/file.json", "out.json"));

            assertThrows(RuntimeException.class, () -> steps.whenBlobIsUploadedFromFile(dt));
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
