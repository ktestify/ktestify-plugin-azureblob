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
package io.github.ktestify.azureblob;

import com.typesafe.config.Config;
import io.github.ktestify.azureblob.config.AzureBlobConfig;
import io.github.ktestify.exceptions.PluginException;
import io.github.ktestify.plugin.KtestifyPlugin;
import io.github.ktestify.plugin.PluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ktestify plugin entry point for Azure Blob Storage.
 *
 * <p>Registers itself via the Java {@link java.util.ServiceLoader} mechanism (see
 * {@code META-INF/services/io.github.ktestify.plugin.KtestifyPlugin}).
 *
 * <h2>What this plugin provides</h2>
 *
 * <ul>
 *   <li>A transport layer — {@link io.github.ktestify.azureblob.io.AzureBlobRecordFetcher} — that polls and downloads
 *       blobs, returning them as {@link io.github.ktestify.models.ConsumedRecord}{@code <String>}.
 *   <li>Cucumber step definitions in {@code io.github.ktestify.azureblob.steps} — auto-injected as a {@code --glue}
 *       package by the ktestify runtime.
 * </ul>
 *
 * <h2>Configuration</h2>
 *
 * All settings live under {@code ktestify.plugins.azure-blob} in the HOCON config tree. Defaults are declared in the
 * {@code reference.conf} bundled with this JAR. Override any value in your {@code application.conf} or via the
 * corresponding environment variable (see {@link AzureBlobConfig}).
 *
 * <h2>Minimum required config</h2>
 *
 * At least one of the following must be set:
 *
 * <ul>
 *   <li>{@code KTESTIFY_AZURE_BLOB_CONNECTION_STRING} — recommended for local / CI use
 *   <li>{@code KTESTIFY_AZURE_BLOB_ACCOUNT_NAME} + {@code KTESTIFY_AZURE_BLOB_ACCOUNT_KEY}
 *   <li>{@code KTESTIFY_AZURE_BLOB_ACCOUNT_NAME} + {@code KTESTIFY_AZURE_BLOB_SAS_TOKEN}
 * </ul>
 *
 * Credentials can also be provided per-scenario via the {@code connectionString} column in the {@code Given Azure Blob
 * Storage container} step DataTable.
 *
 * @since 1.0.0
 * @see AzureBlobConfig
 * @see io.github.ktestify.azureblob.io.AzureBlobRecordFetcher
 */
public final class AzureBlobPlugin implements KtestifyPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(AzureBlobPlugin.class);

    private static final String PLUGIN_ID = "azure-blob";
    private static final String PLUGIN_VERSION = "1.0-SNAPSHOT";
    private static final String PLUGIN_AUTHOR_NAME = "Nil MALHOMME";
    private static final String PLUGIN_AUTHOR_EMAIL = "malhomme.nil+oss@icloud.com";
    private static final String GLUE_PACKAGE = "io.github.ktestify.azureblob.steps";

    /** Cached config — populated during {@link #initialize(PluginContext)}. */
    private AzureBlobConfig config;

    // -------------------------------------------------------------------------
    // KtestifyPlugin contract
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public String getId() {
        return PLUGIN_ID;
    }

    /** {@inheritDoc} */
    @Override
    public String getVersion() {
        return PLUGIN_VERSION;
    }

    /**
     * Returns the name of the plugin author.
     *
     * @return {@code "Nil MALHOMME"}
     */
    @Override
    public String getAuthorName() {
        return PLUGIN_AUTHOR_NAME;
    }

    /**
     * Returns the contact email of the plugin author.
     *
     * @return {@code "malhomme.nil+oss@icloud.com"}
     */
    @Override
    public String getAuthorEmail() {
        return PLUGIN_AUTHOR_EMAIL;
    }

    /**
     * Returns the Cucumber glue package containing all Azure Blob Storage step definitions.
     *
     * @return {@code "io.github.ktestify.azureblob.steps"}
     */
    @Override
    public String getGluePackage() {
        return GLUE_PACKAGE;
    }

    /**
     * Initializes the plugin: loads and validates the Azure Blob Storage configuration.
     *
     * <p>If no authentication credentials are configured a {@link PluginException} is thrown, aborting the run
     * immediately rather than failing at the first step that tries to connect.
     *
     * @param context plugin context providing access to the loaded {@link io.github.ktestify.config.KtestifyConfig}
     * @throws PluginException if the config subtree {@code ktestify.plugins.azure-blob} is missing or if no credentials
     *     are configured
     */
    @Override
    public void initialize(PluginContext context) {
        LOG.info("Initializing ktestify Azure Blob Storage plugin v{}…", PLUGIN_VERSION);

        Config raw = context.getConfig().getRaw();
        if (!raw.hasPath("ktestify.plugins.azure-blob")) {
            throw new PluginException("Azure Blob plugin: missing HOCON section 'ktestify.plugins.azure-blob'. "
                    + "Ensure the plugin JAR (with its reference.conf) is on the classpath.");
        }

        this.config = AzureBlobConfig.from(raw);

        // Warn (not fail) if no credentials are configured — a connection string may be
        // provided per-scenario in the DataTable.
        if (!config.hasConnectionString() && !config.hasAccountKeyAuth() && !config.hasSasToken()) {
            LOG.warn("Azure Blob plugin: no global credentials configured. "
                    + "Set KTESTIFY_AZURE_BLOB_CONNECTION_STRING or provide credentials per-scenario in the "
                    + "Given Azure Blob Storage container DataTable.");
        } else {
            LOG.info(
                    "Azure Blob plugin: credentials configured (connection-string={}, account-key={}, sas-token={}).",
                    config.hasConnectionString(),
                    config.hasAccountKeyAuth(),
                    config.hasSasToken());
        }

        LOG.info(
                "Azure Blob plugin initialized — read-timeout={}ms, poll-interval={}ms.",
                config.getReadTimeoutMs(),
                config.getPollIntervalMs());
    }

    /** No-op shutdown — the Azure SDK manages its own HTTP client lifecycle. */
    @Override
    public void shutdown() {
        LOG.info("Azure Blob Storage plugin shut down.");
    }
}
