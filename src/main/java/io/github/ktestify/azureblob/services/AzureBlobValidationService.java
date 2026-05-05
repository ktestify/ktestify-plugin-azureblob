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

import static io.github.ktestify.match.RecordMatcherFactory.METHOD_MATCH_FILE;

import io.github.ktestify.azureblob.config.AzureBlobConfig;
import io.github.ktestify.azureblob.entities.KtestifyBlobContainer;
import io.github.ktestify.azureblob.io.AzureBlobConsumer;
import io.github.ktestify.azureblob.io.AzureBlobConsumerContext;
import io.github.ktestify.exceptions.ConsumerException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates Azure Blob Storage validation for Cucumber step definitions.
 *
 * <p>Mirrors the design of {@code ConsumerValidationService} in {@code ktestify-cucumber}:
 *
 * <ul>
 *   <li>Builds a typed {@link AzureBlobConsumerContext} from the DataTable row.
 *   <li>Submits an {@link AzureBlobConsumer} to a cached thread pool.
 *   <li>Applies a two-layer timeout (inner: {@link io.github.ktestify.azureblob.io.AzureBlobRecordFetcher}; outer:
 *       executor guard with {@code BUFFER_TIME_MS} extra).
 * </ul>
 *
 * <h2>DataTable column conventions</h2>
 *
 * <table>
 *   <tr><th>Column</th><th>Type</th><th>Description</th></tr>
 *   <tr><td>{@code blobName}</td><td>String</td><td>Exact blob path within the container</td></tr>
 *   <tr><td>{@code file}</td><td>String</td><td>Local expected-content file path (resolved against assets dir)</td></tr>
 *   <tr><td>{@code readTimeout}</td><td>int (seconds)</td><td>How long to poll for the blob</td></tr>
 *   <tr><td>{@code excludedKeys}</td><td>comma-separated</td><td>JSON keys / XML elements to exclude from comparison</td></tr>
 * </table>
 *
 * @since 1.0.0
 */
@Slf4j
public class AzureBlobValidationService {

    /** Extra buffer added on top of the read timeout for the outer executor guard (ms). */
    private static final long BUFFER_TIME_MS = 5_000L;

    private final AzureBlobConfig globalConfig;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** @param globalConfig global plugin config (fallback credentials and timeout defaults) */
    public AzureBlobValidationService(AzureBlobConfig globalConfig) {
        this.globalConfig = globalConfig;
    }

    // =========================================================================
    // Validation methods
    // =========================================================================

    /**
     * Validates blob content against a local expected file.
     *
     * @param row DataTable row ({@code blobName}, {@code file}, {@code readTimeout}, {@code excludedKeys})
     * @param container the resolved blob container entity
     * @param assetsDir optional base directory used to resolve relative file paths (may be {@code null})
     */
    public void validateFromFile(Map<String, String> row, KtestifyBlobContainer container, String assetsDir) {
        String blobName = getRequired(row, "blobName");
        String file = resolve(assetsDir, getRequired(row, "file"));
        List<String> excluded = splitComma(getString(row, "excludedKeys"));
        Long readTimeoutMs = getReadTimeoutMs(row);

        AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                .containerName(container.getContainerName())
                .connectionString(container.getConnectionString())
                .blobName(blobName)
                .matchMethod(METHOD_MATCH_FILE)
                .matchFilePaths(List.of(file))
                .excludedFields(excluded)
                .readTimeoutMs(readTimeoutMs)
                .build();

        execute(ctx, container, readTimeoutMs);
    }

    /**
     * Validates blob content against an XML file with optional element exclusions.
     *
     * @param row DataTable row ({@code blobName}, {@code file}, {@code readTimeout}, {@code excludedElements})
     * @param container the resolved blob container entity
     * @param assetsDir optional assets base directory
     */
    public void validateFromXmlFile(Map<String, String> row, KtestifyBlobContainer container, String assetsDir) {
        String blobName = getRequired(row, "blobName");
        String file = resolve(assetsDir, getRequired(row, "file"));
        List<String> excluded = splitComma(getString(row, "excludedElements"));
        Long readTimeoutMs = getReadTimeoutMs(row);

        AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                .containerName(container.getContainerName())
                .connectionString(container.getConnectionString())
                .blobName(blobName)
                .matchMethod(io.github.ktestify.match.RecordMatcherFactory.METHOD_MATCH_XML)
                .matchFilePaths(List.of(file))
                .excludedFields(excluded)
                .readTimeoutMs(readTimeoutMs)
                .build();

        execute(ctx, container, readTimeoutMs);
    }

    /**
     * Asserts that a blob does <em>not</em> exist within the given timeout.
     *
     * <p>If the blob appears within the timeout the step fails. A timeout (blob not found) is treated as the expected
     * outcome (pass).
     *
     * @param row DataTable row ({@code blobName}, {@code readTimeout})
     * @param container the resolved blob container entity
     */
    public void validateBlobAbsent(Map<String, String> row, KtestifyBlobContainer container) {
        String blobName = getRequired(row, "blobName");
        Long readTimeoutMs = getReadTimeoutMs(row);

        // No match method → NoOpRecordMatcher — if fetch succeeds (blob appeared), fail
        AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                .containerName(container.getContainerName())
                .connectionString(container.getConnectionString())
                .blobName(blobName)
                .readTimeoutMs(readTimeoutMs)
                .build();

        boolean found;
        try {
            found = runWithTimeout(new AzureBlobConsumer(ctx, globalConfig), readTimeoutMs);
        } catch (ConsumerException e) {
            // Timeout = blob not found = expected
            log.info("Blob '{}' not found in container '{}' as expected.", blobName, container.getContainerName());
            return;
        }
        if (found) {
            throw new AssertionError("Expected blob '" + blobName + "' to be absent in container '"
                    + container.getContainerName() + "', but it was found.");
        }
    }

    // =========================================================================
    // Private execution helpers
    // =========================================================================

    private void execute(AzureBlobConsumerContext ctx, KtestifyBlobContainer container, Long readTimeoutMs) {
        boolean passed = runWithTimeout(new AzureBlobConsumer(ctx, globalConfig), readTimeoutMs);
        if (!passed) {
            throw new AssertionError("Azure Blob validation failed for blob '" + ctx.getBlobName() + "' in container '"
                    + container.getContainerName() + "'.");
        }
    }

    private boolean runWithTimeout(java.util.concurrent.Callable<Boolean> consumer, Long readTimeoutMs) {
        long effectiveMs = (readTimeoutMs != null ? readTimeoutMs : globalConfig.getReadTimeoutMs()) + BUFFER_TIME_MS;
        Future<Boolean> future = executor.submit(consumer);
        try {
            return Boolean.TRUE.equals(future.get(effectiveMs, TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ConsumerException("Outer timeout exceeded after " + effectiveMs + "ms.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ConsumerException ce) throw ce;
            throw new ConsumerException("Azure Blob consumer execution failed: " + cause.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConsumerException("Azure Blob consumer thread interrupted.");
        }
    }

    // =========================================================================
    // DataTable helpers
    // =========================================================================

    private static String getString(Map<String, String> row, String col) {
        String v = row.get(col);
        return (v != null && !v.isBlank()) ? v : null;
    }

    private static String getRequired(Map<String, String> row, String col) {
        String v = getString(row, col);
        if (v == null) throw new IllegalArgumentException("Required DataTable column '" + col + "' is missing.");
        return v;
    }

    /** Reads {@code readTimeout} (seconds) and converts to milliseconds. Returns {@code null} if absent. */
    private static Long getReadTimeoutMs(Map<String, String> row) {
        String v = getString(row, "readTimeout");
        return v != null ? Long.parseLong(v.trim()) * 1000L : null;
    }

    private static List<String> splitComma(String value) {
        if (value == null || value.isBlank()) return Collections.emptyList();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    /** Resolves a potentially-relative path against an assets directory. */
    private static String resolve(String assetsDir, String path) {
        if (assetsDir == null || assetsDir.isBlank() || path == null) return path;
        if (java.nio.file.Path.of(path).isAbsolute()) return path;
        return java.nio.file.Path.of(assetsDir, path).toString();
    }
}
