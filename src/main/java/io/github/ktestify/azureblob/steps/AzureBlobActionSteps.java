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
import io.cucumber.java.en.When;
import io.github.ktestify.azureblob.entities.KtestifyBlobContainer;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Cucumber {@code @When} step definitions for Azure Blob Storage actions.
 *
 * <h2>Available steps</h2>
 *
 * <pre>{@code
 * When blob is uploaded from file
 *   | containerAlias | file              | blobName           |
 *   | my-blob        | payloads/data.json | output/result.json |
 * }</pre>
 *
 * <p>The {@code file} path is resolved against the configured assets directory when it is relative.
 *
 * @since 1.0.0
 */
@Slf4j
public class AzureBlobActionSteps {

    private final SharedAzureBlobResources shared;

    /** PicoContainer constructor injection. */
    public AzureBlobActionSteps(SharedAzureBlobResources shared) {
        this.shared = shared;
    }

    // -------------------------------------------------------------------------
    // Step definitions
    // -------------------------------------------------------------------------

    /**
     * Uploads a local file to Azure Blob Storage.
     *
     * <p>DataTable columns:
     *
     * <table>
     *   <tr><th>Column</th><th>Required</th><th>Description</th></tr>
     *   <tr><td>containerAlias</td><td>yes</td><td>Alias (or name) of the target container — must have been declared in
     *       {@code Background}</td></tr>
     *   <tr><td>file</td><td>yes</td><td>Local source file path; relative paths are resolved against the assets
     *       directory</td></tr>
     *   <tr><td>blobName</td><td>yes</td><td>Destination blob path within the container (e.g.
     *       {@code "data/payload.json"})</td></tr>
     * </table>
     *
     * @param dataTable one-row DataTable defining the upload operation
     */
    @When("blob is uploaded from file")
    public void whenBlobIsUploadedFromFile(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);

        String containerAlias = getRequired(row, "containerAlias");
        String file = getRequired(row, "file");
        String blobName = getRequired(row, "blobName");

        KtestifyBlobContainer container = shared.containers.getOrThrow(containerAlias);
        String resolvedFile = resolve(shared.assetsDirectory, file);

        log.info(
                "Uploading file '{}' to blob '{}' in container '{}'…",
                resolvedFile,
                blobName,
                container.getContainerName());

        shared.uploadService.upload(container, blobName, resolvedFile);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String getRequired(Map<String, String> row, String col) {
        String v = row.get(col);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Required DataTable column '" + col + "' is missing.");
        }
        return v.trim();
    }

    private static String resolve(String assetsDir, String path) {
        if (assetsDir == null || assetsDir.isBlank() || path == null) return path;
        if (java.nio.file.Path.of(path).isAbsolute()) return path;
        return java.nio.file.Path.of(assetsDir, path).toString();
    }
}
