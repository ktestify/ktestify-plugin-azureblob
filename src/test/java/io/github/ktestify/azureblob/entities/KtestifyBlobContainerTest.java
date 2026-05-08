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
package io.github.ktestify.azureblob.entities;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

/** Unit tests for {@link KtestifyBlobContainer} — builder, immutability, and field accessors. */
@DisplayName("KtestifyBlobContainer")
class KtestifyBlobContainerTest {

    @Nested
    @DisplayName("builder — happy path")
    class BuilderHappyPathTests {

        @Test
        @DisplayName("builds with all fields set")
        void buildsWithAllFields() {
            KtestifyBlobContainer c = KtestifyBlobContainer.builder()
                    .containerName("my-container")
                    .containerAlias("my-alias")
                    .connectionString("DefaultEndpointsProtocol=https;AccountName=x;AccountKey=y")
                    .build();

            assertEquals("my-container", c.getContainerName());
            assertEquals("my-alias", c.getContainerAlias());
            assertEquals("DefaultEndpointsProtocol=https;AccountName=x;AccountKey=y", c.getConnectionString());
        }

        @Test
        @DisplayName("builds with only containerName (alias and connectionString may be null)")
        void buildsWithNameOnly() {
            KtestifyBlobContainer c =
                    KtestifyBlobContainer.builder().containerName("minimal").build();

            assertEquals("minimal", c.getContainerName());
            assertNull(c.getContainerAlias());
            assertNull(c.getConnectionString());
        }

        @Test
        @DisplayName("builds with null connectionString (per-container auth not needed)")
        void buildsWithNullConnectionString() {
            KtestifyBlobContainer c = KtestifyBlobContainer.builder()
                    .containerName("c")
                    .containerAlias("a")
                    .connectionString(null)
                    .build();

            assertNull(c.getConnectionString());
        }
    }

    @Nested
    @DisplayName("value semantics (@Value)")
    class ValueSemanticsTests {

        @Test
        @DisplayName("two instances with same fields are equal")
        void equalWhenSameFields() {
            KtestifyBlobContainer a = KtestifyBlobContainer.builder()
                    .containerName("c")
                    .containerAlias("a")
                    .build();
            KtestifyBlobContainer b = KtestifyBlobContainer.builder()
                    .containerName("c")
                    .containerAlias("a")
                    .build();

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("two instances with different containerName are not equal")
        void notEqualWhenDifferentName() {
            KtestifyBlobContainer a =
                    KtestifyBlobContainer.builder().containerName("c1").build();
            KtestifyBlobContainer b =
                    KtestifyBlobContainer.builder().containerName("c2").build();

            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("toString() contains the container name")
        void toStringContainsContainerName() {
            KtestifyBlobContainer c =
                    KtestifyBlobContainer.builder().containerName("my-cont").build();
            assertTrue(c.toString().contains("my-cont"));
        }
    }
}
