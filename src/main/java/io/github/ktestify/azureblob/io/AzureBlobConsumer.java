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

import io.github.ktestify.azureblob.config.AzureBlobConfig;
import io.github.ktestify.exceptions.ConsumerException;
import io.github.ktestify.exceptions.FetchException;
import io.github.ktestify.io.core.AbstractConsumer;
import io.github.ktestify.match.MatchContext;
import io.github.ktestify.match.MatchResult;
import io.github.ktestify.match.RecordMatcher;
import io.github.ktestify.match.RecordMatcherFactory;
import io.github.ktestify.models.ConsumedRecord;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestration-layer consumer for Azure Blob Storage.
 *
 * <p>Follows the same three-layer separation as Kafka consumers in {@code ktestify-core}:
 *
 * <ol>
 *   <li><b>Transport</b> — {@link AzureBlobRecordFetcher}: polls for and downloads the blob.
 *   <li><b>Orchestration</b> — this class: wires fetch → match → result.
 *   <li><b>Assertion</b> — {@link RecordMatcher} implementation selected by {@code matchMethod}.
 * </ol>
 *
 * <p>The consumer is a {@link java.util.concurrent.Callable}{@code <Boolean>} submitted to an
 * {@link java.util.concurrent.ExecutorService} by
 * {@link io.github.ktestify.azureblob.services.AzureBlobValidationService}, which applies its own outer timeout guard.
 *
 * @since 1.0.0
 * @see AzureBlobRecordFetcher
 * @see io.github.ktestify.azureblob.services.AzureBlobValidationService
 */
@Slf4j
public class AzureBlobConsumer extends AbstractConsumer {

    private final AzureBlobConsumerContext context;
    private final AzureBlobConfig globalConfig;
    private final RecordMatcher<String> matcher;

    /**
     * Creates a consumer that resolves its matcher from {@code context.getMatchMethod()}.
     *
     * @param context per-fetch configuration (blob name, timeouts, match method, etc.)
     * @param globalConfig global plugin config (fallback credentials / timeouts)
     */
    public AzureBlobConsumer(AzureBlobConsumerContext context, AzureBlobConfig globalConfig) {
        super(Collections.emptyMap());
        this.context = context;
        this.globalConfig = globalConfig;
        this.matcher = RecordMatcherFactory.forRaw(context.getMatchMethod());
    }

    /**
     * Executes the full fetch-then-match pipeline.
     *
     * @return {@code true} if the blob was found and the matcher passed; {@code false} if the matcher failed (diff
     *     logged at ERROR level)
     * @throws ConsumerException wrapping any {@link FetchException} thrown by the fetcher
     */
    @Override
    public Boolean call() {
        AzureBlobRecordFetcher fetcher = new AzureBlobRecordFetcher(context, globalConfig);
        try {
            List<ConsumedRecord<String>> records = fetcher.fetch();
            MatchContext matchCtx = buildMatchContext();
            MatchResult result = matcher.match(records, matchCtx);

            if (!result.isPassed()) {
                log.error(
                        "Azure Blob content mismatch for '{}' in container '{}':\n{}",
                        context.getBlobName(),
                        context.getContainerName(),
                        result.getDiff());
            }
            return result.isPassed();

        } catch (FetchException e) {
            throw new ConsumerException("Azure Blob fetch failed for '" + context.getBlobName() + "' in container '"
                    + context.getContainerName() + "': " + e.getMessage());
        } finally {
            fetcher.close();
        }
    }

    /**
     * Builds the {@link MatchContext} from this consumer's {@link AzureBlobConsumerContext}.
     *
     * <p>Sub-classes may override to inject additional fields (e.g. a custom {@code matchKey}).
     *
     * @return the match context
     */
    protected MatchContext buildMatchContext() {
        return MatchContext.builder()
                .matchMethod(context.getMatchMethod())
                .matchFilePaths(context.getMatchFilePaths())
                .excludedFields(context.getExcludedFields())
                .strictMatching(false)
                .build();
    }
}
