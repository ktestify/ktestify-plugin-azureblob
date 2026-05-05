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
package io.github.ktestify.azureblob.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.Getter;

/**
 * Typed configuration for the Azure Blob Storage plugin.
 *
 * <p>Reads the {@code ktestify.plugins.azure-blob} HOCON subtree. Values can be overridden via environment variables
 * (see {@code reference.conf} in this module).
 *
 * <h2>Environment variables</h2>
 *
 * <table>
 *   <tr><th>Key</th><th>Env var</th><th>Default</th></tr>
 *   <tr><td>connection-string</td><td>KTESTIFY_AZURE_BLOB_CONNECTION_STRING</td><td>(empty)</td></tr>
 *   <tr><td>account-name</td><td>KTESTIFY_AZURE_BLOB_ACCOUNT_NAME</td><td>(empty)</td></tr>
 *   <tr><td>account-key</td><td>KTESTIFY_AZURE_BLOB_ACCOUNT_KEY</td><td>(empty)</td></tr>
 *   <tr><td>sas-token</td><td>KTESTIFY_AZURE_BLOB_SAS_TOKEN</td><td>(empty)</td></tr>
 *   <tr><td>read-timeout</td><td>KTESTIFY_AZURE_BLOB_READ_TIMEOUT</td><td>30s</td></tr>
 *   <tr><td>poll-interval</td><td>—</td><td>500ms</td></tr>
 * </table>
 *
 * @since 1.0.0
 */
@Getter
public final class AzureBlobConfig {

    private static final String CONFIG_PATH = "ktestify.plugins.azure-blob";

    /** Azure Storage connection string. Takes priority over account-name + account-key authentication. */
    private final String connectionString;

    /** Azure Storage account name (used when connection-string is blank). */
    private final String accountName;

    /** Azure Storage account key (used together with {@link #accountName}). */
    private final String accountKey;

    /** Shared Access Signature (SAS) token — alternative to account-key authentication. */
    private final String sasToken;

    /**
     * Maximum time in milliseconds to wait for a blob to appear before throwing a
     * {@link io.github.ktestify.exceptions.FetchException}. Defaults to 30 000 ms.
     */
    private final long readTimeoutMs;

    /** Interval in milliseconds between existence polls when waiting for a blob to appear. Defaults to 500 ms. */
    private final long pollIntervalMs;

    private AzureBlobConfig(Config cfg) {
        this.connectionString = cfg.getString("connection-string");
        this.accountName = cfg.getString("account-name");
        this.accountKey = cfg.getString("account-key");
        this.sasToken = cfg.getString("sas-token");
        this.readTimeoutMs = cfg.getDuration("read-timeout").toMillis();
        this.pollIntervalMs = cfg.getDuration("poll-interval").toMillis();
    }

    /**
     * Parses the plugin config from the full application {@link Config} object. Typically called as:
     *
     * <pre>
     * AzureBlobConfig cfg = AzureBlobConfig.from(ctx.getConfig().getRaw());
     * </pre>
     *
     * @param root the root application config (the full {@code ktestify.*} tree)
     * @return a populated {@code AzureBlobConfig}
     */
    public static AzureBlobConfig from(Config root) {
        Config merged = root.withFallback(ConfigFactory.load()).resolve();
        return new AzureBlobConfig(merged.getConfig(CONFIG_PATH));
    }

    /**
     * Returns {@code true} if a connection string has been configured. Connection strings take priority over
     * account-name + account-key authentication.
     *
     * @return {@code true} when {@link #connectionString} is non-blank
     */
    public boolean hasConnectionString() {
        return connectionString != null && !connectionString.isBlank();
    }

    /**
     * Returns {@code true} if account-name + account-key authentication has been configured.
     *
     * @return {@code true} when both {@link #accountName} and {@link #accountKey} are non-blank
     */
    public boolean hasAccountKeyAuth() {
        return accountName != null && !accountName.isBlank() && accountKey != null && !accountKey.isBlank();
    }

    /**
     * Returns {@code true} if a SAS token has been configured.
     *
     * @return {@code true} when {@link #sasToken} is non-blank
     */
    public boolean hasSasToken() {
        return sasToken != null && !sasToken.isBlank();
    }
}
