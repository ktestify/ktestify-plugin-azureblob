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
 * Unit tests for {@link AzureBlobBackgroundSteps}.
 *
 * <p>These tests exercise the step logic in isolation — no Cucumber runner, no Docker.
 */
@DisplayName("AzureBlobBackgroundSteps")
class AzureBlobBackgroundStepsTest {

    private SharedAzureBlobResources shared;
    private AzureBlobBackgroundSteps steps;

    @BeforeEach
    void setUp() {
        KtestifyConfig.reset();
        shared = new SharedAzureBlobResources();
        steps = new AzureBlobBackgroundSteps(shared);
    }

    @AfterEach
    void tearDown() {
        KtestifyConfig.reset();
    }

    // =========================================================================
    // givenAzureBlobContainer (single)
    // =========================================================================

    @Nested
    @DisplayName("Given Azure Blob Storage container (single)")
    class GivenSingleContainerTests {

        @Test
        @DisplayName("registers container by name and alias when both are provided")
        void registersContainerByNameAndAlias() {
            DataTable dt = buildDataTable(
                    List.of("containerName", "containerAlias", "connectionString"),
                    List.of("my-container", "my-alias", "connStr123"));

            steps.givenAzureBlobContainer(dt);

            // Retrieve by name
            KtestifyBlobContainer byName = shared.containers.getOrThrow("my-container");
            assertEquals("my-container", byName.getContainerName());
            assertEquals("my-alias", byName.getContainerAlias());
            assertEquals("connStr123", byName.getConnectionString());

            // Retrieve by alias — same object
            KtestifyBlobContainer byAlias = shared.containers.getOrThrow("my-alias");
            assertSame(byName, byAlias);
        }

        @Test
        @DisplayName("registers container by name only when alias is blank")
        void registersContainerByNameOnlyWhenAliasBlank() {
            DataTable dt = buildDataTable(
                    List.of("containerName", "containerAlias", "connectionString"),
                    List.of("my-container", "", ""));

            steps.givenAzureBlobContainer(dt);

            assertTrue(shared.containers.contains("my-container"));
            assertFalse(shared.containers.contains(""));
        }

        @Test
        @DisplayName("registers container without connection string (global config fallback)")
        void registersContainerWithoutConnectionString() {
            DataTable dt = buildDataTable(
                    List.of("containerName", "containerAlias"),
                    List.of("no-connstr-container", "alias"));

            steps.givenAzureBlobContainer(dt);

            KtestifyBlobContainer c = shared.containers.getOrThrow("alias");
            assertEquals("no-connstr-container", c.getContainerName());
            assertNull(c.getConnectionString());
        }

        @Test
        @DisplayName("throws IllegalArgumentException when containerName is blank")
        void throwsWhenContainerNameBlank() {
            DataTable dt = buildDataTable(
                    List.of("containerName", "containerAlias"),
                    List.of("", "alias"));

            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> steps.givenAzureBlobContainer(dt));
            assertTrue(ex.getMessage().contains("containerName"));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when containerName column is missing")
        void throwsWhenContainerNameColumnMissing() {
            // DataTable has only containerAlias column — containerName key will be null
            DataTable dt = buildDataTable(List.of("containerAlias"), List.of("alias-only"));

            assertThrows(IllegalArgumentException.class, () -> steps.givenAzureBlobContainer(dt));
        }

        @Test
        @DisplayName("registers container with connection string when provided")
        void registersWithConnectionString() {
            String connStr = "DefaultEndpointsProtocol=https;AccountName=test;AccountKey=dGVzdA==";
            DataTable dt = buildDataTable(
                    List.of("containerName", "containerAlias", "connectionString"),
                    List.of("secured-container", "sec", connStr));

            steps.givenAzureBlobContainer(dt);

            KtestifyBlobContainer c = shared.containers.getOrThrow("sec");
            assertEquals(connStr, c.getConnectionString());
        }
    }

    // =========================================================================
    // givenAzureBlobContainers (multiple)
    // =========================================================================

    @Nested
    @DisplayName("Given Azure Blob Storage containers (multiple)")
    class GivenMultipleContainersTests {

        @Test
        @DisplayName("registers all containers from a multi-row DataTable")
        void registersAllContainers() {
            DataTable dt = buildMultiRowDataTable(
                    List.of("containerName", "containerAlias", "connectionString"),
                    List.of("container-1", "alias-1", "conn-1"),
                    List.of("container-2", "alias-2", "conn-2"),
                    List.of("container-3", "alias-3", "conn-3"));

            steps.givenAzureBlobContainers(dt);

            assertEquals("container-1", shared.containers.getOrThrow("alias-1").getContainerName());
            assertEquals("container-2", shared.containers.getOrThrow("alias-2").getContainerName());
            assertEquals("container-3", shared.containers.getOrThrow("alias-3").getContainerName());
        }

        @Test
        @DisplayName("each container is retrievable by both name and alias")
        void eachContainerRetrievableByNameAndAlias() {
            DataTable dt = buildMultiRowDataTable(
                    List.of("containerName", "containerAlias"),
                    List.of("cont-a", "alias-a"),
                    List.of("cont-b", "alias-b"));

            steps.givenAzureBlobContainers(dt);

            assertSame(shared.containers.getOrThrow("cont-a"), shared.containers.getOrThrow("alias-a"));
            assertSame(shared.containers.getOrThrow("cont-b"), shared.containers.getOrThrow("alias-b"));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when any row has blank containerName")
        void throwsWhenAnyRowHasBlankContainerName() {
            DataTable dt = buildMultiRowDataTable(
                    List.of("containerName", "containerAlias"),
                    List.of("valid-container", "valid-alias"),
                    List.of("", "bad-alias")); // blank name in second row

            assertThrows(IllegalArgumentException.class, () -> steps.givenAzureBlobContainers(dt));
        }

        @Test
        @DisplayName("registers containers without alias (alias column empty)")
        void registersContainersWithoutAlias() {
            DataTable dt = buildMultiRowDataTable(
                    List.of("containerName", "containerAlias"),
                    List.of("cont-x", ""),
                    List.of("cont-y", ""));

            steps.givenAzureBlobContainers(dt);

            assertTrue(shared.containers.contains("cont-x"));
            assertTrue(shared.containers.contains("cont-y"));
        }
    }

    // =========================================================================
    // givenAzureBlobAssetsDirectory
    // =========================================================================

    @Nested
    @DisplayName("Given Azure Blob assets directory")
    class GivenAssetsDirectoryTests {

        @Test
        @DisplayName("sets shared.assetsDirectory to the provided path")
        void setsAssetsDirectory() {
            DataTable dt =
                    buildDataTable(List.of("absolutePath"), List.of("/home/user/test/resources/data"));

            steps.givenAzureBlobAssetsDirectory(dt);

            assertEquals("/home/user/test/resources/data", shared.assetsDirectory);
        }

        @Test
        @DisplayName("overwrites a previously set assets directory")
        void overwritesPreviousDirectory() {
            shared.assetsDirectory = "/old/path";

            DataTable dt = buildDataTable(List.of("absolutePath"), List.of("/new/path"));
            steps.givenAzureBlobAssetsDirectory(dt);

            assertEquals("/new/path", shared.assetsDirectory);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when absolutePath is blank")
        void throwsWhenPathBlank() {
            DataTable dt = buildDataTable(List.of("absolutePath"), List.of(""));

            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> steps.givenAzureBlobAssetsDirectory(dt));
            assertTrue(ex.getMessage().contains("absolutePath"));
        }

        @Test
        @DisplayName("throws IllegalArgumentException when absolutePath column is absent")
        void throwsWhenPathColumnAbsent() {
            // DataTable without the required column
            DataTable dt = buildDataTable(List.of("wrongColumn"), List.of("some-value"));

            assertThrows(IllegalArgumentException.class, () -> steps.givenAzureBlobAssetsDirectory(dt));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Creates a single-row DataTable from header and value lists, with a proper TableConverter so
     * that {@code dataTable.asMaps()} works outside a Cucumber runtime.
     */
    private static DataTable buildDataTable(List<String> headers, List<String> values) {
        DataTableTypeRegistry registry = new DataTableTypeRegistry(Locale.ENGLISH);
        DataTableTypeRegistryTableConverter converter = new DataTableTypeRegistryTableConverter(registry);
        return DataTable.create(List.of(headers, values), converter);
    }

    /**
     * Creates a multi-row DataTable (first list = headers, remaining = data rows) with a proper
     * TableConverter so that {@code dataTable.asMaps()} works outside a Cucumber runtime.
     */
    @SafeVarargs
    private static DataTable buildMultiRowDataTable(List<String> headers, List<String>... dataRows) {
        DataTableTypeRegistry registry = new DataTableTypeRegistry(Locale.ENGLISH);
        DataTableTypeRegistryTableConverter converter = new DataTableTypeRegistryTableConverter(registry);
        List<List<String>> all = new java.util.ArrayList<>();
        all.add(headers);
        all.addAll(java.util.Arrays.asList(dataRows));
        return DataTable.create(all, converter);
    }
}



