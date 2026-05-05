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

import lombok.Builder;
import lombok.Value;

/**
 * Immutable entity representing an Azure Blob Storage container registered in a Cucumber scenario.
 *
 * <p>Created by the {@code Given Azure Blob Storage container} step and stored in
 * {@link io.github.ktestify.azureblob.steps.SharedAzureBlobResources} keyed by both name and alias.
 *
 * <h2>Example</h2>
 *
 * <pre>
 * Given Azure Blob Storage container
 *   | containerName | containerAlias | connectionString                |
 *   | my-container  | my-blob        | DefaultEndpointsProtocol=https... |
 * </pre>
 *
 * <p>If {@code connectionString} is left blank the global value from
 * {@code ktestify.plugins.azure-blob.connection-string} is used at fetch time.
 *
 * @since 1.0.0
 */
@Value
@Builder
public class KtestifyBlobContainer {

    /** The physical Azure Blob Storage container name (as it appears in the storage account). Must be non-null. */
    String containerName;

    /**
     * Optional alias used to reference this container from other steps. If blank, only {@code containerName} can be
     * used as the lookup key.
     */
    String containerAlias;

    /**
     * Optional Azure Storage connection string for this specific container. When {@code null} or blank, the global
     * {@code ktestify.plugins.azure-blob.connection-string} is used.
     */
    String connectionString;
}
