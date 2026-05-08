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

import static org.junit.jupiter.api.Assertions.*;

import com.azure.storage.blob.BlobContainerClient;
import com.typesafe.config.ConfigFactory;
import io.github.ktestify.azureblob.config.AzureBlobConfig;
import io.github.ktestify.azureblob.extensions.AzuriteTestExtension;
import io.github.ktestify.exceptions.FetchException;
import io.github.ktestify.models.ConsumedRecord;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for {@link AzureBlobRecordFetcher} using a real Azurite container.
 *
 * <h2>Test scenarios</h2>
 *
 * <ul>
 *   <li>Fetch a blob that exists immediately — returns the correct content and metadata.
 *   <li>Fetch with a short timeout when no blob exists — throws {@link FetchException}.
 *   <li>Poll-until-present — blob is uploaded asynchronously; fetcher must wait and succeed.
 *   <li>Blob metadata is propagated to {@link ConsumedRecord#getHeaders()}.
 *   <li>{@code close()} is idempotent — calling it twice does not throw.
 * </ul>
 */
@DisplayName("AzureBlobRecordFetcher — Integration Tests")
@ExtendWith(AzuriteTestExtension.class)
class AzureBlobRecordFetcherITTests {

    private static final String CONTAINER = AzuriteTestExtension.TEST_CONTAINER;

    private AzureBlobConfig config;

    @BeforeEach
    void setUp() {
        // Load config pointing at the running Azurite instance
        config = AzureBlobConfig.from(ConfigFactory.parseString("ktestify.plugins.azure-blob.connection-string = \""
                + AzuriteTestExtension.getConnectionString() + "\"\n"
                + "ktestify.plugins.azure-blob.read-timeout = 5s\n"
                + "ktestify.plugins.azure-blob.poll-interval = 100ms"));

        // Wipe the container state between tests
        AzuriteTestExtension.clearContainer(CONTAINER);
    }

    // =========================================================================
    // Successful fetch
    // =========================================================================

    @Nested
    @DisplayName("fetch() — blob present")
    class FetchBlobPresentTests {

        @Test
        @DisplayName("returns a single ConsumedRecord with the blob content")
        void returnsRecordWithContent() throws Exception {
            // arrange
            String blobName = "fetch-test/payload.json";
            String content = "{\"id\": \"1\", \"name\": \"ktestify\"}";
            uploadBlob(CONTAINER, blobName, content);

            AzureBlobConsumerContext ctx = contextFor(CONTAINER, blobName);
            AzureBlobRecordFetcher fetcher = new AzureBlobRecordFetcher(ctx, config);

            // act
            List<ConsumedRecord<String>> records = fetcher.fetch();

            // assert
            assertEquals(1, records.size());
            ConsumedRecord<String> record = records.get(0);
            assertEquals(content, record.getValue());
            assertEquals(blobName, record.getKey());
            assertEquals(CONTAINER, record.getSource());
            assertEquals(0, record.getPartition());
            assertEquals(-1L, record.getOffset());
            assertNotNull(record.getTimestamp());

            fetcher.close();
        }

        @Test
        @DisplayName("fetches plain-text blob content correctly")
        void fetchesPlainTextContent() throws Exception {
            String blobName = "fetch-test/message.txt";
            String content = "Hello from Azurite!";
            uploadBlob(CONTAINER, blobName, content);

            AzureBlobConsumerContext ctx = contextFor(CONTAINER, blobName);
            AzureBlobRecordFetcher fetcher = new AzureBlobRecordFetcher(ctx, config);

            List<ConsumedRecord<String>> records = fetcher.fetch();

            assertEquals(content, records.get(0).getValue());
            fetcher.close();
        }

        @Test
        @DisplayName("blob metadata is propagated to ConsumedRecord headers")
        void metadataPropagatedToHeaders() throws Exception {
            String blobName = "fetch-test/with-meta.json";
            String content = "{\"status\": \"ok\"}";

            // Upload with metadata
            BlobContainerClient containerClient =
                    AzuriteTestExtension.buildBlobServiceClient().getBlobContainerClient(CONTAINER);
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            containerClient.getBlobClient(blobName).upload(new java.io.ByteArrayInputStream(bytes), bytes.length, true);
            // Set metadata after upload
            containerClient.getBlobClient(blobName).setMetadata(java.util.Map.of("env", "test", "version", "1"));

            AzureBlobConsumerContext ctx = contextFor(CONTAINER, blobName);
            AzureBlobRecordFetcher fetcher = new AzureBlobRecordFetcher(ctx, config);

            List<ConsumedRecord<String>> records = fetcher.fetch();
            ConsumedRecord<String> record = records.get(0);

            assertEquals("test", record.getHeaders().get("env"));
            assertEquals("1", record.getHeaders().get("version"));

            fetcher.close();
        }

        @Test
        @DisplayName("close() is idempotent — no exception when called twice")
        void closeIsIdempotent() throws Exception {
            String blobName = "fetch-test/close-test.json";
            uploadBlob(CONTAINER, blobName, "{}");

            AzureBlobConsumerContext ctx = contextFor(CONTAINER, blobName);
            AzureBlobRecordFetcher fetcher = new AzureBlobRecordFetcher(ctx, config);
            fetcher.fetch();

            assertDoesNotThrow(fetcher::close);
            assertDoesNotThrow(fetcher::close); // second call must not throw
        }
    }

    // =========================================================================
    // Timeout / not found
    // =========================================================================

    @Nested
    @DisplayName("fetch() — blob absent / timeout")
    class FetchTimeoutTests {

        @Test
        @DisplayName("throws FetchException when blob does not exist within the timeout")
        void throwsFetchExceptionOnTimeout() {
            // Very short timeout so the test stays fast
            AzureBlobConfig fastConfig =
                    AzureBlobConfig.from(ConfigFactory.parseString("ktestify.plugins.azure-blob.connection-string = \""
                            + AzuriteTestExtension.getConnectionString() + "\"\n"
                            + "ktestify.plugins.azure-blob.read-timeout = 500ms\n"
                            + "ktestify.plugins.azure-blob.poll-interval = 100ms"));

            AzureBlobConsumerContext ctx = contextFor(CONTAINER, "nonexistent/blob.json");
            AzureBlobRecordFetcher fetcher = new AzureBlobRecordFetcher(ctx, fastConfig);

            FetchException ex = assertThrows(FetchException.class, fetcher::fetch);
            assertTrue(ex.getMessage().contains("nonexistent/blob.json"), "error should mention blob name");
            assertTrue(ex.getMessage().contains(CONTAINER), "error should mention container name");
        }

        @Test
        @DisplayName("error message contains timeout duration")
        void errorMessageContainsTimeoutMs() {
            AzureBlobConfig fastConfig =
                    AzureBlobConfig.from(ConfigFactory.parseString("ktestify.plugins.azure-blob.connection-string = \""
                            + AzuriteTestExtension.getConnectionString() + "\"\n"
                            + "ktestify.plugins.azure-blob.read-timeout = 300ms\n"
                            + "ktestify.plugins.azure-blob.poll-interval = 100ms"));

            AzureBlobConsumerContext ctx = contextFor(CONTAINER, "missing.txt");
            AzureBlobRecordFetcher fetcher = new AzureBlobRecordFetcher(ctx, fastConfig);

            FetchException ex = assertThrows(FetchException.class, fetcher::fetch);
            assertTrue(ex.getMessage().contains("300"), "message should include timeout value");
        }
    }

    // =========================================================================
    // Async upload — poll-until-present
    // =========================================================================

    @Nested
    @DisplayName("fetch() — poll-until-present")
    class PollUntilPresentTests {

        @Test
        @DisplayName("fetcher waits and succeeds when blob is uploaded asynchronously")
        void waitsThenSucceedsWhenBlobUploadedAsync() throws Exception {
            String blobName = "async/late-upload.json";
            String content = "{\"late\": true}";

            // Schedule the upload 800ms after the fetcher starts
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> uploadBlob(CONTAINER, blobName, content), 800, TimeUnit.MILLISECONDS);

            AzureBlobConfig waitingConfig =
                    AzureBlobConfig.from(ConfigFactory.parseString("ktestify.plugins.azure-blob.connection-string = \""
                            + AzuriteTestExtension.getConnectionString() + "\"\n"
                            + "ktestify.plugins.azure-blob.read-timeout = 5s\n"
                            + "ktestify.plugins.azure-blob.poll-interval = 200ms"));

            AzureBlobConsumerContext ctx = contextFor(CONTAINER, blobName);
            AzureBlobRecordFetcher fetcher = new AzureBlobRecordFetcher(ctx, waitingConfig);

            List<ConsumedRecord<String>> records = fetcher.fetch();

            assertEquals(content, records.get(0).getValue());
            scheduler.shutdownNow();
            fetcher.close();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static AzureBlobConsumerContext contextFor(String container, String blobName) {
        return AzureBlobConsumerContext.builder()
                .containerName(container)
                .blobName(blobName)
                .build();
    }

    private static void uploadBlob(String containerName, String blobName, String content) {
        try {
            BlobContainerClient client =
                    AzuriteTestExtension.buildBlobServiceClient().getBlobContainerClient(containerName);
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            client.getBlobClient(blobName).upload(new java.io.ByteArrayInputStream(bytes), bytes.length, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload test blob '" + blobName + "': " + e.getMessage(), e);
        }
    }
}
