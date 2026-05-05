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

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.github.ktestify.azureblob.entities.KtestifyBlobContainer;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Cucumber {@code @Given} step definitions for Azure Blob Storage setup.
 *
 * <p>These steps register containers into {@link SharedAzureBlobResources} so they can be referenced by alias from
 * action and validation steps.
 *
 * <h2>Example usage</h2>
 *
 * <pre>{@code
 * Background:
 *   Given Azure Blob Storage container
 *     | containerName | containerAlias | connectionString                                                      |
 *     | my-container  | my-blob        | DefaultEndpointsProtocol=https;AccountName=...;AccountKey=...;EndpointSuffix=core.windows.net |
 * }</pre>
 *
 * <p>The {@code connectionString} column is optional. When blank the global value from
 * {@code ktestify.plugins.azure-blob.connection-string} (or env var {@code KTESTIFY_AZURE_BLOB_CONNECTION_STRING}) is
 * used automatically.
 *
 * @since 1.0.0
 */
@Slf4j
public class AzureBlobBackgroundSteps {

    private final SharedAzureBlobResources shared;

    /** PicoContainer constructor injection. */
    public AzureBlobBackgroundSteps(SharedAzureBlobResources shared) {
        this.shared = shared;
    }

    // -------------------------------------------------------------------------
    // Step definitions
    // -------------------------------------------------------------------------

    /**
     * Registers one Azure Blob Storage container.
     *
     * <p>DataTable columns:
     *
     * <table>
     *   <tr><th>Column</th><th>Required</th><th>Description</th></tr>
     *   <tr><td>containerName</td><td>yes</td><td>Physical container name in the storage account</td></tr>
     *   <tr><td>containerAlias</td><td>no</td><td>Alias used in subsequent steps</td></tr>
     *   <tr><td>connectionString</td><td>no</td><td>Azure Storage connection string (overrides global config)</td></tr>
     * </table>
     *
     * @param dataTable one-row DataTable defining the container
     */
    @Given("Azure Blob Storage container")
    public void givenAzureBlobContainer(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);

        String containerName = row.get("containerName");
        String containerAlias = row.get("containerAlias");
        String connectionString = row.get("connectionString");

        if (containerName == null || containerName.isBlank()) {
            throw new IllegalArgumentException("DataTable column 'containerName' is required for Azure Blob Storage.");
        }

        KtestifyBlobContainer container = KtestifyBlobContainer.builder()
                .containerName(containerName)
                .containerAlias(containerAlias)
                .connectionString(connectionString)
                .build();

        shared.containers.register(containerName, containerAlias, container);
        log.info("Registered Azure Blob Storage container '{}' (alias: '{}').", containerName, containerAlias);
    }

    /**
     * Registers multiple Azure Blob Storage containers in a single step.
     *
     * <p>Each row in the DataTable follows the same column convention as {@link #givenAzureBlobContainer(DataTable)}.
     *
     * @param dataTable multi-row DataTable defining each container
     */
    @Given("Azure Blob Storage containers")
    public void givenAzureBlobContainers(DataTable dataTable) {
        for (Map<String, String> row : dataTable.asMaps()) {
            String containerName = row.get("containerName");
            String containerAlias = row.get("containerAlias");
            String connectionString = row.get("connectionString");

            if (containerName == null || containerName.isBlank()) {
                throw new IllegalArgumentException(
                        "DataTable column 'containerName' is required for each Azure Blob Storage row.");
            }

            KtestifyBlobContainer container = KtestifyBlobContainer.builder()
                    .containerName(containerName)
                    .containerAlias(containerAlias)
                    .connectionString(connectionString)
                    .build();

            shared.containers.register(containerName, containerAlias, container);
            log.info("Registered Azure Blob Storage container '{}' (alias: '{}').", containerName, containerAlias);
        }
    }

    /**
     * Overrides the assets directory for the current scenario. Useful when you need a different base path for this
     * plugin's steps only.
     *
     * <p>DataTable columns:
     *
     * <table>
     *   <tr><th>Column</th><th>Required</th><th>Description</th></tr>
     *   <tr><td>absolutePath</td><td>yes</td><td>Absolute path to the assets directory</td></tr>
     * </table>
     *
     * @param dataTable one-row DataTable with column {@code absolutePath}
     */
    @Given("Azure Blob assets directory")
    public void givenAzureBlobAssetsDirectory(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);
        String path = row.get("absolutePath");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "DataTable column 'absolutePath' is required for 'Given Azure Blob assets directory'.");
        }
        shared.assetsDirectory = path;
        log.info("Azure Blob assets directory set to '{}'.", path);
    }
}
