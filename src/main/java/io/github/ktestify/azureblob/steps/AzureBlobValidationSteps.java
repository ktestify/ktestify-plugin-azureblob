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
package io.github.ktestify.azureblob.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.github.ktestify.azureblob.entities.KtestifyBlobContainer;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Cucumber {@code @Then} and {@code @And} step definitions for Azure Blob Storage validations.
 *
 * <h2>Available steps</h2>
 *
 * <h3>Content validation against a file</h3>
 *
 * <pre>{@code
 * Then expected blob content from file
 *   | containerAlias | blobName            | file          | readTimeout | excludedKeys |
 *   | my-blob        | output/result.json  | expected.json | 30          | timestamp,id |
 * }</pre>
 *
 * <h3>XML content validation</h3>
 *
 * <pre>{@code
 * Then expected blob XML content from file
 *   | containerAlias | blobName         | file         | readTimeout | excludedElements    |
 *   | my-blob        | output/order.xml | expected.xml | 30          | ns:CreationDateTime |
 * }</pre>
 *
 * <h3>Negative assertion — blob must NOT exist</h3>
 *
 * <pre>{@code
 * And blob should not exist
 *   | containerAlias | blobName            | readTimeout |
 *   | my-blob        | output/result.json  | 10          |
 * }</pre>
 *
 * <h3>Positive assertion — blob must exist</h3>
 *
 * <pre>{@code
 * And blob should exist
 *   | containerAlias | blobName            | readTimeout |
 *   | my-blob        | output/result.json  | 30          |
 * }</pre>
 *
 * <h2>DataTable column reference</h2>
 *
 * <table>
 *   <tr><th>Column</th><th>Required</th><th>Description</th></tr>
 *   <tr><td>containerAlias</td><td>yes</td><td>Alias (or name) of the container declared in Background</td></tr>
 *   <tr><td>blobName</td><td>yes</td><td>Exact blob path within the container</td></tr>
 *   <tr><td>file</td><td>validation steps only</td><td>Local expected-content file; resolved against assets dir</td></tr>
 *   <tr><td>readTimeout</td><td>no</td><td>Seconds to wait for the blob (default: global config read-timeout)</td></tr>
 *   <tr><td>excludedKeys</td><td>no</td><td>Comma-separated JSON field names to ignore during comparison</td></tr>
 *   <tr><td>excludedElements</td><td>no</td><td>Comma-separated XML element names to ignore (XML steps only)</td></tr>
 * </table>
 *
 * @since 1.0.0
 */
@Slf4j
public class AzureBlobValidationSteps {

    private final SharedAzureBlobResources shared;

    /** PicoContainer constructor injection. */
    public AzureBlobValidationSteps(SharedAzureBlobResources shared) {
        this.shared = shared;
    }

    // -------------------------------------------------------------------------
    // Step definitions
    // -------------------------------------------------------------------------

    /**
     * Validates blob content against a local expected file.
     *
     * <p>The blob is polled until it exists or the read timeout expires. Content is then compared using the
     * {@code FileRecordMatcher} (character-level diff on mismatch).
     *
     * @param dataTable DataTable row with columns: {@code containerAlias}, {@code blobName}, {@code file},
     *     {@code readTimeout} (optional), {@code excludedKeys} (optional)
     */
    @Then("expected blob content from file")
    public void thenExpectedBlobContentFromFile(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);
        KtestifyBlobContainer container = resolveContainer(row);
        log.info("Validating blob '{}' in container '{}'…", row.get("blobName"), container.getContainerName());
        shared.validationService.validateFromFile(row, container, shared.assetsDirectory);
    }

    /**
     * Validates blob content against a local XML expected file with optional element exclusions.
     *
     * @param dataTable DataTable row with columns: {@code containerAlias}, {@code blobName}, {@code file},
     *     {@code readTimeout} (optional), {@code excludedElements} (optional)
     */
    @Then("expected blob XML content from file")
    public void thenExpectedBlobXmlContentFromFile(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);
        KtestifyBlobContainer container = resolveContainer(row);
        log.info("Validating XML blob '{}' in container '{}'…", row.get("blobName"), container.getContainerName());
        shared.validationService.validateFromXmlFile(row, container, shared.assetsDirectory);
    }

    /**
     * Asserts that a blob does <em>not</em> appear within the given timeout (negative assertion / watcher).
     *
     * <p>A timeout (blob not found) is the expected outcome — the step passes. If the blob appears within the timeout,
     * the step fails.
     *
     * @param dataTable DataTable row with columns: {@code containerAlias}, {@code blobName}, {@code readTimeout}
     *     (optional)
     */
    @And("blob should not exist")
    public void andBlobShouldNotExist(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);
        KtestifyBlobContainer container = resolveContainer(row);
        log.info(
                "Asserting blob '{}' does not exist in container '{}'…",
                row.get("blobName"),
                container.getContainerName());
        shared.validationService.validateBlobAbsent(row, container);
    }

    /**
     * Asserts that a blob exists within the given timeout (positive existence check, no content comparison).
     *
     * @param dataTable DataTable row with columns: {@code containerAlias}, {@code blobName}, {@code readTimeout}
     *     (optional)
     */
    @And("blob should exist")
    public void andBlobShouldExist(DataTable dataTable) {
        Map<String, String> row = dataTable.asMaps().get(0);
        KtestifyBlobContainer container = resolveContainer(row);
        log.info("Asserting blob '{}' exists in container '{}'…", row.get("blobName"), container.getContainerName());
        // No match method → NoOpRecordMatcher (passes if blob is found)
        shared.validationService.validateFromFile(
                // Inject a dummy file value — NoOpRecordMatcher ignores it
                new java.util.HashMap<>(row) {
                    {
                        putIfAbsent("file", "__existence_check__");
                    }
                },
                container,
                shared.assetsDirectory);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private KtestifyBlobContainer resolveContainer(Map<String, String> row) {
        String alias = row.get("containerAlias");
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("DataTable column 'containerAlias' is required.");
        }
        return shared.containers.getOrThrow(alias);
    }
}
