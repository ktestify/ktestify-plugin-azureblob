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

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Immutable context object that configures an {@link AzureBlobRecordFetcher} for a single fetch operation.
 *
 * <p>Mirrors the design of {@code ConsumerContext} in {@code ktestify-core}: it is a pure value object built by the
 * service layer ({@link io.github.ktestify.azureblob.services.AzureBlobValidationService}) and consumed by the
 * transport layer ({@link AzureBlobRecordFetcher}).
 *
 * <h2>Authentication priority</h2>
 *
 * <ol>
 *   <li>{@link #connectionString} (if non-blank — highest priority)
 *   <li>{@link #accountName} + {@link #accountKey}
 *   <li>{@link #sasToken} + {@link #accountName}
 *   <li>Global config ({@code ktestify.plugins.azure-blob}) as fallback
 * </ol>
 *
 * @since 1.0.0
 * @see AzureBlobRecordFetcher
 */
@Value
@Builder
public class AzureBlobConsumerContext {

    /** The Azure Blob Storage container name. Must be non-null. */
    String containerName;

    /** The exact blob name (path) to fetch. Must be non-null. */
    String blobName;

    /**
     * Azure Storage connection string. When non-blank, overrides any account-name / account-key / SAS credentials.
     * Falls back to {@code ktestify.plugins.azure-blob.connection-string} when blank or {@code null}.
     */
    String connectionString;

    /** Azure Storage account name. Required when using account-key or SAS-token authentication. */
    String accountName;

    /** Azure Storage account key. Used together with {@link #accountName}. */
    String accountKey;

    /** Shared Access Signature token. Used together with {@link #accountName}. */
    String sasToken;

    /**
     * Match method — same constants as {@code ConfigConstants.methodMatch*} (e.g. {@code "methodMatchFile"},
     * {@code "methodMatchXML"}). May be {@code null} to skip content comparison and only assert blob existence.
     */
    String matchMethod;

    /**
     * Ordered list of expected-content file paths used by the matcher. Single-record matchers use
     * {@code matchFilePaths.get(0)}; batch matchers iterate by index. Defaults to an empty list.
     */
    @Builder.Default
    List<String> matchFilePaths = Collections.emptyList();

    /**
     * Field names (or XML element names) to exclude during comparison. Passed directly to the matcher as
     * {@code MatchContext.excludedFields}. Defaults to an empty list.
     */
    @Builder.Default
    List<String> excludedFields = Collections.emptyList();

    /**
     * Maximum time in milliseconds to poll for the blob before throwing a
     * {@link io.github.ktestify.exceptions.FetchException}. When {@code null} the global config value
     * ({@code read-timeout}) is used.
     */
    Long readTimeoutMs;

    /**
     * Interval in milliseconds between existence polls. When {@code null} the global config value
     * ({@code poll-interval}) is used.
     */
    Long pollIntervalMs;

    /**
     * Convenience accessor — returns the first match file path, or {@code null} if the list is empty. Mirrors the
     * pattern of {@code MatchContext.getMatchFilePath()} in {@code ktestify-core}.
     *
     * @return the first element of {@link #matchFilePaths}, or {@code null}
     */
    public String getMatchFilePath() {
        return matchFilePaths != null && !matchFilePaths.isEmpty() ? matchFilePaths.get(0) : null;
    }
}
