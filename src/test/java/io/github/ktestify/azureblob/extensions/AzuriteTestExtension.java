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
package io.github.ktestify.azureblob.extensions;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * JUnit 5 extension that starts an Azurite container (Microsoft Azure Storage emulator) once per test class and tears
 * it down afterwards.
 *
 * <p>Uses {@link GenericContainer} directly — rather than {@code AzuriteContainer} — so we can pass
 * {@code --skipApiVersionCheck} on the command line. {@code AzuriteContainer.configure()} overrides any
 * {@code withCommand()} call, making the flag impossible to inject through the typed wrapper.
 *
 * <h2>Connection string</h2>
 *
 * Azurite exposes a fixed development account ({@code devstoreaccount1}) with a well-known key that is safe to commit —
 * it only works against the local emulator.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * @ExtendWith(AzuriteTestExtension.class)
 * class MyBlobIT {
 *     @Test void test() {
 *         String connStr = AzuriteTestExtension.getConnectionString();
 *     }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class AzuriteTestExtension implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOG = LoggerFactory.getLogger(AzuriteTestExtension.class);

    private static final String AZURITE_IMAGE = "mcr.microsoft.com/azure-storage/azurite:latest";

    /** Blob service port inside the container. */
    private static final int BLOB_PORT = 10000;

    /**
     * Well-known Azurite development account name.
     * Safe to commit — only valid against the local emulator.
     */
    public static final String DEV_ACCOUNT_NAME = "devstoreaccount1";

    /**
     * Well-known Azurite development account key.
     * Safe to commit — only valid against the local emulator.
     */
    public static final String DEV_ACCOUNT_KEY =
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    /** Default blob container created in Azurite for all tests in this suite. */
    public static final String TEST_CONTAINER = "ktestify-test";

    @SuppressWarnings("resource")
    private static GenericContainer<?> azurite;

    // -------------------------------------------------------------------------
    // Extension lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void beforeAll(ExtensionContext context) {
        if (azurite == null || !azurite.isRunning()) {
            LOG.info("Starting Azurite container ({}) with --skipApiVersionCheck…", AZURITE_IMAGE);
            azurite = new GenericContainer<>(DockerImageName.parse(AZURITE_IMAGE))
                    .withExposedPorts(BLOB_PORT, 10001, 10002)
                    .withCommand(
                            "azurite",
                            "--blobHost", "0.0.0.0",
                            "--queueHost", "0.0.0.0",
                            "--tableHost", "0.0.0.0",
                            "--skipApiVersionCheck");
            azurite.start();
            LOG.info("Azurite started — blob endpoint: {}", getBlobEndpoint());

            createContainer(TEST_CONTAINER);
            LOG.info("Created default test container '{}'.", TEST_CONTAINER);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        // Container left running for the full test suite — Ryuk / JVM shutdown cleans it up.
    }

    // -------------------------------------------------------------------------
    // Public helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the blob endpoint URL for the running Azurite instance.
     *
     * @return e.g. {@code http://localhost:32769/devstoreaccount1}
     */
    public static String getBlobEndpoint() {
        assertStarted();
        return "http://" + azurite.getHost() + ":" + azurite.getMappedPort(BLOB_PORT) + "/" + DEV_ACCOUNT_NAME;
    }

    /**
     * Returns the full Azure Storage connection string for the running Azurite instance.
     *
     * @return a connection string targeting the local emulator
     * @throws IllegalStateException if the container has not been started yet
     */
    public static String getConnectionString() {
        assertStarted();
        return "DefaultEndpointsProtocol=http"
                + ";AccountName=" + DEV_ACCOUNT_NAME
                + ";AccountKey=" + DEV_ACCOUNT_KEY
                + ";BlobEndpoint=" + getBlobEndpoint() + ";";
    }

    /**
     * Builds a {@link BlobServiceClient} connected to the running Azurite instance.
     *
     * @return a ready-to-use {@code BlobServiceClient}
     */
    public static BlobServiceClient buildBlobServiceClient() {
        return new BlobServiceClientBuilder()
                .connectionString(getConnectionString())
                .buildClient();
    }

    /**
     * Creates a blob container in Azurite if it does not already exist.
     *
     * @param containerName the container name
     * @return the {@link BlobContainerClient} for the container
     */
    public static BlobContainerClient createContainer(String containerName) {
        BlobContainerClient client = buildBlobServiceClient().getBlobContainerClient(containerName);
        if (!Boolean.TRUE.equals(client.exists())) {
            client.create();
        }
        return client;
    }

    /**
     * Deletes all blobs inside the given container without removing the container itself.
     * Useful for cleaning state between tests.
     *
     * @param containerName the container to wipe
     */
    public static void clearContainer(String containerName) {
        BlobContainerClient client = buildBlobServiceClient().getBlobContainerClient(containerName);
        if (Boolean.TRUE.equals(client.exists())) {
            client.listBlobs().forEach(item -> client.getBlobClient(item.getName()).deleteIfExists());
        }
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private static void assertStarted() {
        if (azurite == null || !azurite.isRunning()) {
            throw new IllegalStateException(
                    "AzuriteTestExtension has not been started. "
                            + "Annotate your test class with @ExtendWith(AzuriteTestExtension.class).");
        }
    }
}
