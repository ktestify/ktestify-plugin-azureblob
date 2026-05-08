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

import static io.github.ktestify.match.RecordMatcherFactory.METHOD_MATCH_FILE;
import static org.junit.jupiter.api.Assertions.*;

import com.azure.storage.blob.BlobContainerClient;
import com.typesafe.config.ConfigFactory;
import io.github.ktestify.azureblob.config.AzureBlobConfig;
import io.github.ktestify.azureblob.extensions.AzuriteTestExtension;
import io.github.ktestify.exceptions.ConsumerException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for {@link AzureBlobConsumer} using a real Azurite container.
 *
 * <h2>Test scenarios</h2>
 *
 * <ul>
 *   <li>Full fetch-then-match pipeline: blob content matches file → {@code true}.
 *   <li>Content mismatch: blob differs from file → {@code false}.
 *   <li>Blob absent: fetcher times out → {@link ConsumerException} is propagated.
 *   <li>No-op matcher (null matchMethod) → always returns {@code true}.
 * </ul>
 */
@DisplayName("AzureBlobConsumer — Integration Tests")
@ExtendWith(AzuriteTestExtension.class)
class AzureBlobConsumerITTests {

    private static final String CONTAINER = AzuriteTestExtension.TEST_CONTAINER;

    private AzureBlobConfig config;

    @BeforeEach
    void setUp() {
        config = AzureBlobConfig.from(ConfigFactory.parseString("ktestify.plugins.azure-blob.connection-string = \""
                + AzuriteTestExtension.getConnectionString() + "\"\n"
                + "ktestify.plugins.azure-blob.read-timeout = 5s\n"
                + "ktestify.plugins.azure-blob.poll-interval = 100ms"));

        AzuriteTestExtension.clearContainer(CONTAINER);
    }

    // =========================================================================
    // Successful match
    // =========================================================================

    @Nested
    @DisplayName("call() — content matches")
    class ContentMatchesTests {

        @Test
        @DisplayName("returns true when blob content equals expected file")
        void returnsTrueOnMatch(@TempDir Path tempDir) throws Exception {
            String content = "{\"orderId\": \"ORD-001\", \"status\": \"PROCESSED\"}";
            String blobName = "consumer/match.json";
            uploadBlob(CONTAINER, blobName, content);

            Path expectedFile = tempDir.resolve("expected.json");
            Files.writeString(expectedFile, content);

            AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                    .containerName(CONTAINER)
                    .blobName(blobName)
                    .matchMethod(METHOD_MATCH_FILE)
                    .matchFilePaths(List.of(expectedFile.toString()))
                    .build();

            Boolean result = new AzureBlobConsumer(ctx, config).call();
            assertTrue(result);
        }

        @Test
        @DisplayName("getMatchFilePath() returns first path from matchFilePaths")
        void getMatchFilePathReturnsFirstElement(@TempDir Path tempDir) throws Exception {
            Path f1 = tempDir.resolve("a.json");
            Path f2 = tempDir.resolve("b.json");
            Files.writeString(f1, "{}");
            Files.writeString(f2, "{}");

            AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                    .containerName(CONTAINER)
                    .blobName("irrelevant")
                    .matchFilePaths(List.of(f1.toString(), f2.toString()))
                    .build();

            assertEquals(f1.toString(), ctx.getMatchFilePath());
        }
    }

    // =========================================================================
    // Content mismatch
    // =========================================================================

    @Nested
    @DisplayName("call() — content mismatch")
    class ContentMismatchTests {

        @Test
        @DisplayName("returns false when blob content differs from expected file")
        void returnsFalseOnMismatch(@TempDir Path tempDir) throws Exception {
            String blobContent = "{\"orderId\": \"ORD-001\"}";
            String expectedContent = "{\"orderId\": \"ORD-999\"}";
            String blobName = "consumer/mismatch.json";
            uploadBlob(CONTAINER, blobName, blobContent);

            Path expectedFile = tempDir.resolve("expected.json");
            Files.writeString(expectedFile, expectedContent);

            AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                    .containerName(CONTAINER)
                    .blobName(blobName)
                    .matchMethod(METHOD_MATCH_FILE)
                    .matchFilePaths(List.of(expectedFile.toString()))
                    .build();

            Boolean result = new AzureBlobConsumer(ctx, config).call();
            assertFalse(result);
        }
    }

    // =========================================================================
    // No-op matcher
    // =========================================================================

    @Nested
    @DisplayName("call() — no match method (existence check only)")
    class NoOpMatcherTests {

        @Test
        @DisplayName("returns true when blob exists and matchMethod is null")
        void returnsTrueWhenBlobExistsNoMatcher() throws Exception {
            String blobName = "consumer/exists.json";
            uploadBlob(CONTAINER, blobName, "{\"exists\": true}");

            AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                    .containerName(CONTAINER)
                    .blobName(blobName)
                    .matchMethod(null) // → NoOpRecordMatcher
                    .build();

            Boolean result = new AzureBlobConsumer(ctx, config).call();
            assertTrue(result);
        }
    }

    // =========================================================================
    // Fetch failure propagation
    // =========================================================================

    @Nested
    @DisplayName("call() — blob absent")
    class BlobAbsentTests {

        @Test
        @DisplayName("throws ConsumerException when blob times out")
        void throwsConsumerExceptionOnTimeout() {
            AzureBlobConfig fastConfig =
                    AzureBlobConfig.from(ConfigFactory.parseString("ktestify.plugins.azure-blob.connection-string = \""
                            + AzuriteTestExtension.getConnectionString() + "\"\n"
                            + "ktestify.plugins.azure-blob.read-timeout = 400ms\n"
                            + "ktestify.plugins.azure-blob.poll-interval = 100ms"));

            AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                    .containerName(CONTAINER)
                    .blobName("nonexistent/blob.json")
                    .matchMethod(METHOD_MATCH_FILE)
                    .build();

            ConsumerException ex =
                    assertThrows(ConsumerException.class, () -> new AzureBlobConsumer(ctx, fastConfig).call());
            assertTrue(ex.getMessage().contains("nonexistent/blob.json"));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void uploadBlob(String containerName, String blobName, String content) {
        try {
            BlobContainerClient client =
                    AzuriteTestExtension.buildBlobServiceClient().getBlobContainerClient(containerName);
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            client.getBlobClient(blobName).upload(new ByteArrayInputStream(bytes), bytes.length, true);
        } catch (Exception e) {
            throw new RuntimeException("Upload failed: " + e.getMessage(), e);
        }
    }
}
