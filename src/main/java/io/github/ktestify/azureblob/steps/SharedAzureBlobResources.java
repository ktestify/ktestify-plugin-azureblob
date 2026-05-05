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

import io.github.ktestify.azureblob.config.AzureBlobConfig;
import io.github.ktestify.azureblob.entities.KtestifyBlobContainer;
import io.github.ktestify.azureblob.services.AzureBlobUploadService;
import io.github.ktestify.azureblob.services.AzureBlobValidationService;
import io.github.ktestify.config.KtestifyConfig;
import io.github.ktestify.manager.ObjectManager;

/**
 * PicoContainer-managed shared state for the Azure Blob Storage plugin steps.
 *
 * <p>A single instance is created per Cucumber scenario by PicoContainer and injected into every Azure Blob step class
 * that declares it as a constructor parameter. This ensures the container registry and services share the same
 * lifecycle as the scenario.
 *
 * <h2>Assets directory</h2>
 *
 * <p>{@link #assetsDirectory} is pre-populated from {@code ktestify.framework.directories.assets} in the loaded config.
 * The {@code Given assets directory} step in {@code ktestify-cucumber} overrides this for a specific scenario. Because
 * this class only depends on {@code ktestify-core}, it reads the config directly rather than importing
 * {@code SharedStepsResources} from {@code ktestify-cucumber}.
 *
 * @since 1.0.0
 */
public class SharedAzureBlobResources {

    /**
     * Registry for Azure Blob Storage containers, keyed by container name and/or alias. Populated by
     * {@link AzureBlobBackgroundSteps}.
     */
    public final ObjectManager<KtestifyBlobContainer> containers = new ObjectManager<>();

    /**
     * Global plugin configuration loaded once per scenario instance. Used by services as a credential / timeout
     * fallback.
     */
    public final AzureBlobConfig config;

    /** Upload service — shared within the scenario so a single HTTP client pool is reused. */
    public final AzureBlobUploadService uploadService;

    /** Validation service — shared within the scenario (single thread pool). */
    public final AzureBlobValidationService validationService;

    /**
     * The assets base directory for the current scenario. Pre-populated from
     * {@code ktestify.framework.directories.assets}; may be {@code null} if not configured.
     */
    public String assetsDirectory;

    /** Initialised by PicoContainer at the start of each scenario. */
    public SharedAzureBlobResources() {
        KtestifyConfig cfg = KtestifyConfig.getOrLoad();
        this.config = AzureBlobConfig.from(cfg.getRaw());
        this.uploadService = new AzureBlobUploadService(config);
        this.validationService = new AzureBlobValidationService(config);

        // Pre-populate assets directory from global config
        cfg.getFramework()
                .getAssetsDirectory()
                .filter(path -> !path.isBlank())
                .ifPresent(path -> this.assetsDirectory = path);
    }
}
