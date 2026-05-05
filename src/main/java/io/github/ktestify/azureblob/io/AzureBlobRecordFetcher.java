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
package io.github.ktestify.azureblob.io;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobProperties;
import io.github.ktestify.azureblob.config.AzureBlobConfig;
import io.github.ktestify.exceptions.FetchException;
import io.github.ktestify.io.core.RecordFetcher;
import io.github.ktestify.models.ConsumedRecord;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transport-layer implementation of {@link RecordFetcher} for Azure Blob Storage.
 *
 * <p>Polls for a specific blob by name until it exists (or the read timeout expires). Returns the blob's content as a
 * {@link ConsumedRecord}{@code <String>} — the common currency shared with all ktestify matchers.
 *
 * <h2>Authentication</h2>
 *
 * Authentication is resolved from {@link AzureBlobConsumerContext} in the following priority order:
 *
 * <ol>
 *   <li>Connection string ({@code context.connectionString}) — highest priority
 *   <li>Account name + account key ({@code context.accountName} + {@code context.accountKey})
 *   <li>SAS token ({@code context.accountName} + {@code context.sasToken})
 *   <li>Global plugin config ({@code ktestify.plugins.azure-blob}) — lowest priority
 * </ol>
 *
 * <h2>Polling</h2>
 *
 * When the blob does not yet exist the fetcher sleeps {@code pollIntervalMs} and retries until {@code readTimeoutMs} is
 * exhausted. This supports the typical E2E pattern where the SUT writes a blob asynchronously after receiving a
 * command.
 *
 * <h2>ConsumedRecord field mapping</h2>
 *
 * <table>
 *   <tr><th>ConsumedRecord field</th><th>Azure Blob source</th></tr>
 *   <tr><td>source</td><td>container name</td></tr>
 *   <tr><td>partition</td><td>0 (no partitioning concept)</td></tr>
 *   <tr><td>offset</td><td>-1 (no offset concept)</td></tr>
 *   <tr><td>key</td><td>blob name (path)</td></tr>
 *   <tr><td>value</td><td>blob content as UTF-8 String</td></tr>
 *   <tr><td>timestamp</td><td>blob last-modified time</td></tr>
 *   <tr><td>headers</td><td>blob metadata (String key-value pairs)</td></tr>
 * </table>
 *
 * @since 1.0.0
 * @see AzureBlobConsumerContext
 * @see io.github.ktestify.azureblob.io.AzureBlobConsumer
 */
public class AzureBlobRecordFetcher implements RecordFetcher<String> {

    private static final Logger LOG = LoggerFactory.getLogger(AzureBlobRecordFetcher.class);

    private final AzureBlobConsumerContext context;
    private final AzureBlobConfig globalConfig;
    private final BlobContainerClient containerClient;

    /**
     * Creates a fetcher backed by the given context and global plugin config.
     *
     * <p>The {@link BlobContainerClient} is built eagerly so credential errors surface at construction time rather than
     * during {@link #fetch()}.
     *
     * @param context the per-fetch context (blob name, timeouts, auth overrides)
     * @param globalConfig the global plugin config (fallback connection string / timeouts)
     */
    public AzureBlobRecordFetcher(AzureBlobConsumerContext context, AzureBlobConfig globalConfig) {
        this.context = context;
        this.globalConfig = globalConfig;
        this.containerClient = buildContainerClient();
    }

    // -------------------------------------------------------------------------
    // RecordFetcher contract
    // -------------------------------------------------------------------------

    /**
     * Blocks until the configured blob exists (or the read timeout expires), then downloads and returns its content.
     *
     * @return a single-element list containing the blob as a {@link ConsumedRecord}{@code <String>}
     * @throws FetchException if the blob is not found within the timeout, the download fails, or the thread is
     *     interrupted
     */
    @Override
    public List<ConsumedRecord<String>> fetch() throws FetchException {
        String blobName = context.getBlobName();
        String containerName = context.getContainerName();
        long deadlineMs = System.currentTimeMillis() + resolveReadTimeoutMs();
        long pollMs = resolvePollIntervalMs();

        LOG.info(
                "Waiting for blob '{}' in container '{}' (timeout={}ms, poll={}ms)…",
                blobName,
                containerName,
                resolveReadTimeoutMs(),
                pollMs);

        while (System.currentTimeMillis() < deadlineMs) {
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            if (Boolean.TRUE.equals(blobClient.exists())) {
                LOG.info("Blob '{}' found — downloading content.", blobName);
                return List.of(downloadBlob(blobClient, containerName, blobName));
            }

            LOG.debug("Blob '{}' not yet present — retrying in {}ms…", blobName, pollMs);
            sleep(pollMs, blobName);
        }

        throw new FetchException(String.format(
                "Timed out after %dms waiting for blob '%s' in container '%s'.",
                resolveReadTimeoutMs(), blobName, containerName));
    }

    /**
     * No-op — the Azure SDK manages connection pooling internally; there is no resource to release per fetcher
     * instance.
     */
    @Override
    public void close() {
        // BlobContainerClient does not implement AutoCloseable in SDK v12.
        // The underlying HttpClient is shared / managed globally by the SDK.
        LOG.debug("AzureBlobRecordFetcher closed (no-op).");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Downloads the blob and wraps it in a {@link ConsumedRecord}. */
    private ConsumedRecord<String> downloadBlob(BlobClient blobClient, String containerName, String blobName) {
        try {
            byte[] bytes = blobClient.downloadContent().toBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);

            BlobProperties props = blobClient.getProperties();
            Instant lastModified = props.getLastModified().toInstant();
            Map<String, String> headers = extractMetadata(props);

            LOG.debug("Downloaded blob '{}' — {} bytes.", blobName, bytes.length);
            return new ConsumedRecord<>(containerName, 0, -1L, blobName, content, lastModified, headers);
        } catch (Exception e) {
            throw new FetchException("Failed to download blob '" + blobName + "': " + e.getMessage(), e);
        }
    }

    /** Extracts blob metadata as an unmodifiable String map (headers). */
    private static Map<String, String> extractMetadata(BlobProperties props) {
        if (props.getMetadata() == null || props.getMetadata().isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(props.getMetadata()));
    }

    /** Builds a {@link BlobContainerClient} from the resolved credentials. */
    private BlobContainerClient buildContainerClient() {
        String containerName = context.getContainerName();
        BlobContainerClientBuilder builder = new BlobContainerClientBuilder().containerName(containerName);

        // Priority 1: context connection string
        String connStr = context.getConnectionString();
        if (connStr != null && !connStr.isBlank()) {
            LOG.debug("Azure Blob auth: connection string (context override).");
            return builder.connectionString(connStr).buildClient();
        }

        // Priority 2: global config connection string
        if (globalConfig.hasConnectionString()) {
            LOG.debug("Azure Blob auth: connection string (global config).");
            return builder.connectionString(globalConfig.getConnectionString()).buildClient();
        }

        // Priority 3: account name + account key
        String accountName = context.getAccountName();
        String accountKey = context.getAccountKey();
        if (accountName != null && !accountName.isBlank() && accountKey != null && !accountKey.isBlank()) {
            LOG.debug("Azure Blob auth: account name + account key.");
            return builder.endpoint("https://" + accountName + ".blob.core.windows.net")
                    .credential(new com.azure.storage.common.StorageSharedKeyCredential(accountName, accountKey))
                    .buildClient();
        }

        // Priority 4: global account-key auth
        if (globalConfig.hasAccountKeyAuth()) {
            LOG.debug("Azure Blob auth: account name + account key (global config).");
            return builder.endpoint("https://" + globalConfig.getAccountName() + ".blob.core.windows.net")
                    .credential(new com.azure.storage.common.StorageSharedKeyCredential(
                            globalConfig.getAccountName(), globalConfig.getAccountKey()))
                    .buildClient();
        }

        // Priority 5: SAS token
        String sasToken = context.getSasToken() != null ? context.getSasToken() : globalConfig.getSasToken();
        String accountForSas =
                accountName != null && !accountName.isBlank() ? accountName : globalConfig.getAccountName();
        if (sasToken != null && !sasToken.isBlank() && accountForSas != null && !accountForSas.isBlank()) {
            LOG.debug("Azure Blob auth: SAS token.");
            return builder.endpoint("https://" + accountForSas + ".blob.core.windows.net")
                    .sasToken(sasToken)
                    .buildClient();
        }

        throw new io.github.ktestify.exceptions.PluginException(
                "Azure Blob Storage: no authentication credentials configured. "
                        + "Set KTESTIFY_AZURE_BLOB_CONNECTION_STRING or account-name + account-key.");
    }

    private long resolveReadTimeoutMs() {
        return context.getReadTimeoutMs() != null ? context.getReadTimeoutMs() : globalConfig.getReadTimeoutMs();
    }

    private long resolvePollIntervalMs() {
        return context.getPollIntervalMs() != null ? context.getPollIntervalMs() : globalConfig.getPollIntervalMs();
    }

    private static void sleep(long ms, String blobName) throws FetchException {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("Interrupted while waiting for blob '" + blobName + "'.");
        }
    }
}
