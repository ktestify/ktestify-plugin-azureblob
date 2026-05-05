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
package io.github.ktestify.azureblob.services;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import io.github.ktestify.azureblob.config.AzureBlobConfig;
import io.github.ktestify.azureblob.entities.KtestifyBlobContainer;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for uploading files to Azure Blob Storage.
 *
 * <p>Called by the {@code When blob is uploaded from file} Cucumber step. Each upload creates or overwrites a blob at
 * the specified path within the container.
 *
 * <h2>Authentication</h2>
 *
 * The container's {@code connectionString} field takes priority; if blank, the global
 * {@code ktestify.plugins.azure-blob.connection-string} is used.
 *
 * @since 1.0.0
 */
@Slf4j
public class AzureBlobUploadService {

    private final AzureBlobConfig globalConfig;

    /** @param globalConfig global plugin configuration used as credential fallback */
    public AzureBlobUploadService(AzureBlobConfig globalConfig) {
        this.globalConfig = globalConfig;
    }

    /**
     * Uploads a local file to Azure Blob Storage.
     *
     * <p>If the blob already exists it is overwritten ({@code overwrite = true}). The blob's content type is inferred
     * from the file extension when possible; otherwise {@code application/octet-stream} is used.
     *
     * @param container the target container entity (provides name + optional connection string)
     * @param blobName the destination blob path within the container (e.g. {@code "data/payload.json"})
     * @param sourceFile the absolute local path of the file to upload
     * @throws RuntimeException if the file cannot be read or the upload fails
     */
    public void upload(KtestifyBlobContainer container, String blobName, String sourceFile) {
        log.info("Uploading '{}' → blob '{}' in container '{}'…", sourceFile, blobName, container.getContainerName());

        BlobContainerClient containerClient = buildContainerClient(container);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        try {
            byte[] content = Files.readAllBytes(Path.of(sourceFile));
            blobClient.upload(new ByteArrayInputStream(content), content.length, /* overwrite */ true);
            log.info("Upload complete — blob '{}' ({} bytes).", blobName, content.length);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to upload '" + sourceFile + "' to blob '" + blobName + "': " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private BlobContainerClient buildContainerClient(KtestifyBlobContainer container) {
        String connStr = container.getConnectionString() != null
                        && !container.getConnectionString().isBlank()
                ? container.getConnectionString()
                : globalConfig.getConnectionString();

        if (connStr == null || connStr.isBlank()) {
            throw new io.github.ktestify.exceptions.PluginException(
                    "Azure Blob Storage: no connection string configured for container '"
                            + container.getContainerName() + "'. "
                            + "Set KTESTIFY_AZURE_BLOB_CONNECTION_STRING or provide it in the step DataTable.");
        }

        return new BlobContainerClientBuilder()
                .connectionString(connStr)
                .containerName(container.getContainerName())
                .buildClient();
    }
}
