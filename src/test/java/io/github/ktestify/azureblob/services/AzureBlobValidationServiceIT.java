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

import static org.junit.jupiter.api.Assertions.*;

import com.azure.storage.blob.BlobContainerClient;
import com.typesafe.config.ConfigFactory;
import io.github.ktestify.azureblob.config.AzureBlobConfig;
import io.github.ktestify.azureblob.entities.KtestifyBlobContainer;
import io.github.ktestify.azureblob.extensions.AzuriteTestExtension;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for {@link AzureBlobValidationService} using a real Azurite container.
 *
 * <h2>Scenarios</h2>
 *
 * <ul>
 *   <li>{@code validateFromFile} — blob matches file → no assertion error.
 *   <li>{@code validateFromFile} — content mismatch → {@link AssertionError}.
 *   <li>{@code validateFromFile} — blob absent → {@link io.github.ktestify.exceptions.ConsumerException}.
 *   <li>{@code validateFromXmlFile} — XML comparison with optional exclusions.
 *   <li>{@code validateBlobAbsent} — blob does not exist within timeout → passes.
 *   <li>{@code validateBlobAbsent} — blob appears within timeout → {@link AssertionError}.
 * </ul>
 */
@DisplayName("AzureBlobValidationService — Integration Tests")
@ExtendWith(AzuriteTestExtension.class)
class AzureBlobValidationServiceIT {

    private static final String CONTAINER_NAME = AzuriteTestExtension.TEST_CONTAINER;

    private AzureBlobConfig config;
    private AzureBlobValidationService service;
    private KtestifyBlobContainer container;

    @BeforeEach
    void setUp() {
        config = AzureBlobConfig.from(ConfigFactory.parseString("ktestify.plugins.azure-blob.connection-string = \""
                + AzuriteTestExtension.getConnectionString() + "\"\n"
                + "ktestify.plugins.azure-blob.read-timeout = 5s\n"
                + "ktestify.plugins.azure-blob.poll-interval = 100ms"));

        service = new AzureBlobValidationService(config);

        container = KtestifyBlobContainer.builder()
                .containerName(CONTAINER_NAME)
                .connectionString(AzuriteTestExtension.getConnectionString())
                .build();

        AzuriteTestExtension.clearContainer(CONTAINER_NAME);
    }

    // =========================================================================
    // validateFromFile
    // =========================================================================

    @Nested
    @DisplayName("validateFromFile()")
    class ValidateFromFileTests {

        @Test
        @DisplayName("passes when blob content equals expected file")
        void passesOnMatch(@TempDir Path tempDir) throws Exception {
            String content = "{\"id\": \"test-001\", \"status\": \"OK\"}";
            String blobName = "validate/result.json";
            uploadBlob(CONTAINER_NAME, blobName, content);

            Path expectedFile = tempDir.resolve("expected.json");
            Files.writeString(expectedFile, content);

            Map<String, String> row = Map.of("blobName", blobName, "file", expectedFile.toString(), "readTimeout", "5");

            assertDoesNotThrow(() -> service.validateFromFile(row, container, null));
        }

        @Test
        @DisplayName("resolves relative file path against assetsDir")
        void resolvesRelativePath(@TempDir Path tempDir) throws Exception {
            String content = "{\"check\": \"resolved\"}";
            String blobName = "validate/resolved.json";
            uploadBlob(CONTAINER_NAME, blobName, content);

            // Write expected file to a sub-directory
            Path assetsDir = tempDir.resolve("assets");
            Files.createDirectories(assetsDir);
            Path expectedFile = assetsDir.resolve("expected.json");
            Files.writeString(expectedFile, content);

            Map<String, String> row = Map.of(
                    "blobName", blobName,
                    "file", "expected.json", // relative
                    "readTimeout", "5");

            assertDoesNotThrow(() -> service.validateFromFile(row, container, assetsDir.toString()));
        }

        @Test
        @DisplayName("throws AssertionError when blob content does not match expected file")
        void throwsAssertionErrorOnMismatch(@TempDir Path tempDir) throws Exception {
            String blobContent = "{\"id\": \"real\"}";
            String expectedContent = "{\"id\": \"different\"}";
            String blobName = "validate/mismatch.json";
            uploadBlob(CONTAINER_NAME, blobName, blobContent);

            Path expectedFile = tempDir.resolve("expected.json");
            Files.writeString(expectedFile, expectedContent);

            Map<String, String> row = Map.of("blobName", blobName, "file", expectedFile.toString(), "readTimeout", "5");

            assertThrows(AssertionError.class, () -> service.validateFromFile(row, container, null));
        }

        @Test
        @DisplayName("throws ConsumerException when blob does not appear within timeout")
        void throwsConsumerExceptionOnTimeout(@TempDir Path tempDir) throws Exception {
            Path expectedFile = tempDir.resolve("expected.json");
            Files.writeString(expectedFile, "{}");

            // Short timeout config
            AzureBlobConfig fastConfig =
                    AzureBlobConfig.from(ConfigFactory.parseString("ktestify.plugins.azure-blob.connection-string = \""
                            + AzuriteTestExtension.getConnectionString() + "\"\n"
                            + "ktestify.plugins.azure-blob.read-timeout = 300ms\n"
                            + "ktestify.plugins.azure-blob.poll-interval = 100ms"));

            AzureBlobValidationService fastService = new AzureBlobValidationService(fastConfig);

            Map<String, String> row = Map.of("blobName", "nonexistent/missing.json", "file", expectedFile.toString());

            assertThrows(
                    io.github.ktestify.exceptions.ConsumerException.class,
                    () -> fastService.validateFromFile(row, container, null));
        }
    }

    // =========================================================================
    // validateFromXmlFile
    // =========================================================================

    @Nested
    @DisplayName("validateFromXmlFile()")
    class ValidateFromXmlFileTests {

        @Test
        @DisplayName("passes when XML blob content matches expected file")
        void passesOnXmlMatch(@TempDir Path tempDir) throws Exception {
            String xml = "<order><id>ORD-001</id><status>SHIPPED</status></order>";
            String blobName = "validate/order.xml";
            uploadBlob(CONTAINER_NAME, blobName, xml);

            Path expectedFile = tempDir.resolve("expected.xml");
            Files.writeString(expectedFile, xml);

            Map<String, String> row = Map.of("blobName", blobName, "file", expectedFile.toString(), "readTimeout", "5");

            assertDoesNotThrow(() -> service.validateFromXmlFile(row, container, null));
        }

        @Test
        @DisplayName("passes when excluded XML element differs but is ignored")
        void passesWithExcludedElement(@TempDir Path tempDir) throws Exception {
            String blobXml = "<order><id>ORD-001</id><timestamp>2026-01-01T00:00:00Z</timestamp></order>";
            String expectedXml = "<order><id>ORD-001</id><timestamp>2025-12-31T23:59:59Z</timestamp></order>";
            String blobName = "validate/order-excluded.xml";
            uploadBlob(CONTAINER_NAME, blobName, blobXml);

            Path expectedFile = tempDir.resolve("expected.xml");
            Files.writeString(expectedFile, expectedXml);

            Map<String, String> row = Map.of(
                    "blobName",
                    blobName,
                    "file",
                    expectedFile.toString(),
                    "readTimeout",
                    "5",
                    "excludedElements",
                    "timestamp"); // timestamp ignored

            assertDoesNotThrow(() -> service.validateFromXmlFile(row, container, null));
        }
    }

    // =========================================================================
    // validateBlobAbsent
    // =========================================================================

    @Nested
    @DisplayName("validateBlobAbsent()")
    class ValidateBlobAbsentTests {

        @Test
        @DisplayName("passes when blob does not appear within the timeout (expected outcome)")
        void passesWhenBlobAbsent() {
            AzureBlobConfig fastConfig =
                    AzureBlobConfig.from(ConfigFactory.parseString("ktestify.plugins.azure-blob.connection-string = \""
                            + AzuriteTestExtension.getConnectionString() + "\"\n"
                            + "ktestify.plugins.azure-blob.read-timeout = 400ms\n"
                            + "ktestify.plugins.azure-blob.poll-interval = 100ms"));

            AzureBlobValidationService fastService = new AzureBlobValidationService(fastConfig);

            Map<String, String> row = Map.of(
                    "blobName", "absent/should-not-appear.json",
                    "readTimeout", "1");

            assertDoesNotThrow(() -> fastService.validateBlobAbsent(row, container));
        }

        @Test
        @DisplayName("throws AssertionError when blob is found (unexpected presence)")
        void throwsAssertionErrorWhenBlobPresent() throws Exception {
            String blobName = "absent/surprise.json";
            uploadBlob(CONTAINER_NAME, blobName, "{\"surprise\": true}");

            Map<String, String> row = Map.of("blobName", blobName, "readTimeout", "5");

            assertThrows(AssertionError.class, () -> service.validateBlobAbsent(row, container));
        }
    }

    // =========================================================================
    // AzureBlobUploadService
    // =========================================================================

    @Nested
    @DisplayName("AzureBlobUploadService — upload()")
    class UploadServiceTests {

        @Test
        @DisplayName("uploads a file and makes it available in Azurite")
        void uploadsMakesFileAvailable(@TempDir Path tempDir) throws Exception {
            Path localFile = tempDir.resolve("payload.json");
            String content = "{\"uploaded\": true, \"source\": \"local\"}";
            Files.writeString(localFile, content);

            AzureBlobUploadService uploadService = new AzureBlobUploadService(config);
            uploadService.upload(container, "upload/payload.json", localFile.toString());

            // Verify via fetcher
            AzureBlobConfig verifyConfig = config;
            io.github.ktestify.azureblob.io.AzureBlobConsumerContext ctx =
                    io.github.ktestify.azureblob.io.AzureBlobConsumerContext.builder()
                            .containerName(CONTAINER_NAME)
                            .blobName("upload/payload.json")
                            .build();

            io.github.ktestify.azureblob.io.AzureBlobRecordFetcher fetcher =
                    new io.github.ktestify.azureblob.io.AzureBlobRecordFetcher(ctx, verifyConfig);

            var records = fetcher.fetch();
            assertEquals(content, records.get(0).getValue());
            fetcher.close();
        }

        @Test
        @DisplayName("overwrites an existing blob on second upload")
        void overwritesExistingBlob(@TempDir Path tempDir) throws Exception {
            String blobName = "upload/overwrite.json";
            uploadBlob(CONTAINER_NAME, blobName, "{\"v\": 1}");

            Path updatedFile = tempDir.resolve("v2.json");
            Files.writeString(updatedFile, "{\"v\": 2}");

            AzureBlobUploadService uploadService = new AzureBlobUploadService(config);
            assertDoesNotThrow(() -> uploadService.upload(container, blobName, updatedFile.toString()));

            // Verify the new content
            io.github.ktestify.azureblob.io.AzureBlobConsumerContext ctx =
                    io.github.ktestify.azureblob.io.AzureBlobConsumerContext.builder()
                            .containerName(CONTAINER_NAME)
                            .blobName(blobName)
                            .build();
            var records = new io.github.ktestify.azureblob.io.AzureBlobRecordFetcher(ctx, config).fetch();
            assertEquals("{\"v\": 2}", records.get(0).getValue());
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
