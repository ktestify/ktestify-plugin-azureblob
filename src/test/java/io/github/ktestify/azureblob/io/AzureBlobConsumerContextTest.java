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

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.*;

/**
 * Unit tests for {@link AzureBlobConsumerContext} — builder defaults, field accessors, and
 * {@link AzureBlobConsumerContext#getMatchFilePath()} convenience method.
 */
@DisplayName("AzureBlobConsumerContext")
class AzureBlobConsumerContextTest {

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaultTests {

        @Test
        @DisplayName("matchFilePaths defaults to an empty list")
        void matchFilePathsDefaultsToEmptyList() {
            AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                    .containerName("c")
                    .blobName("b")
                    .build();

            assertNotNull(ctx.getMatchFilePaths());
            assertTrue(ctx.getMatchFilePaths().isEmpty());
        }

        @Test
        @DisplayName("excludedFields defaults to an empty list")
        void excludedFieldsDefaultsToEmptyList() {
            AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                    .containerName("c")
                    .blobName("b")
                    .build();

            assertNotNull(ctx.getExcludedFields());
            assertTrue(ctx.getExcludedFields().isEmpty());
        }

        @Test
        @DisplayName("readTimeoutMs defaults to null (use global config)")
        void readTimeoutMsDefaultsToNull() {
            AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                    .containerName("c")
                    .blobName("b")
                    .build();

            assertNull(ctx.getReadTimeoutMs());
        }

        @Test
        @DisplayName("pollIntervalMs defaults to null (use global config)")
        void pollIntervalMsDefaultsToNull() {
            AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                    .containerName("c")
                    .blobName("b")
                    .build();

            assertNull(ctx.getPollIntervalMs());
        }
    }

    @Nested
    @DisplayName("getMatchFilePath() — convenience accessor")
    class GetMatchFilePathTests {

        @Test
        @DisplayName("returns null when matchFilePaths is empty")
        void returnsNullWhenEmpty() {
            AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                    .containerName("c")
                    .blobName("b")
                    .matchFilePaths(Collections.emptyList())
                    .build();

            assertNull(ctx.getMatchFilePath());
        }

        @Test
        @DisplayName("returns null when matchFilePaths is null (builder override)")
        void returnsNullWhenListIsNull() {
            AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                    .containerName("c")
                    .blobName("b")
                    .matchFilePaths(null)
                    .build();

            assertNull(ctx.getMatchFilePath());
        }

        @Test
        @DisplayName("returns the first element when list has one path")
        void returnsFirstElementForSingletonList() {
            AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                    .containerName("c")
                    .blobName("b")
                    .matchFilePaths(List.of("/data/expected.json"))
                    .build();

            assertEquals("/data/expected.json", ctx.getMatchFilePath());
        }

        @Test
        @DisplayName("returns the first element when list has multiple paths")
        void returnsFirstElementForMultiplePaths() {
            AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                    .containerName("c")
                    .blobName("b")
                    .matchFilePaths(List.of("/data/first.json", "/data/second.json", "/data/third.json"))
                    .build();

            assertEquals("/data/first.json", ctx.getMatchFilePath());
        }
    }

    @Nested
    @DisplayName("full builder — all fields")
    class FullBuilderTests {

        @Test
        @DisplayName("all authentication fields are accessible")
        void allAuthFieldsAccessible() {
            AzureBlobConsumerContext ctx = AzureBlobConsumerContext.builder()
                    .containerName("test-container")
                    .blobName("data/output.json")
                    .connectionString("DefaultEndpointsProtocol=https;AccountName=a;AccountKey=k")
                    .accountName("myaccount")
                    .accountKey("mykey")
                    .sasToken("?sv=2020&sig=abc")
                    .matchMethod("methodMatchFile")
                    .matchFilePaths(List.of("/expected/output.json"))
                    .excludedFields(List.of("timestamp", "id"))
                    .readTimeoutMs(15_000L)
                    .pollIntervalMs(250L)
                    .build();

            assertEquals("test-container", ctx.getContainerName());
            assertEquals("data/output.json", ctx.getBlobName());
            assertEquals("DefaultEndpointsProtocol=https;AccountName=a;AccountKey=k", ctx.getConnectionString());
            assertEquals("myaccount", ctx.getAccountName());
            assertEquals("mykey", ctx.getAccountKey());
            assertEquals("?sv=2020&sig=abc", ctx.getSasToken());
            assertEquals("methodMatchFile", ctx.getMatchMethod());
            assertEquals(List.of("/expected/output.json"), ctx.getMatchFilePaths());
            assertEquals(List.of("timestamp", "id"), ctx.getExcludedFields());
            assertEquals(15_000L, ctx.getReadTimeoutMs());
            assertEquals(250L, ctx.getPollIntervalMs());
        }
    }

    @Nested
    @DisplayName("value semantics (@Value)")
    class ValueSemanticsTests {

        @Test
        @DisplayName("two contexts with identical fields are equal")
        void equalWhenSameFields() {
            AzureBlobConsumerContext a = AzureBlobConsumerContext.builder()
                    .containerName("c")
                    .blobName("b")
                    .build();
            AzureBlobConsumerContext b = AzureBlobConsumerContext.builder()
                    .containerName("c")
                    .blobName("b")
                    .build();

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("two contexts with different blobName are not equal")
        void notEqualWhenDifferentBlobName() {
            AzureBlobConsumerContext a = AzureBlobConsumerContext.builder()
                    .containerName("c")
                    .blobName("blob1.json")
                    .build();
            AzureBlobConsumerContext b = AzureBlobConsumerContext.builder()
                    .containerName("c")
                    .blobName("blob2.json")
                    .build();

            assertNotEquals(a, b);
        }
    }
}
