<p align="center">
  <img src="https://raw.githubusercontent.com/ktestify/.github/refs/heads/main/profile/assets/png/ktestify-banner-2x.png" alt="ktestify-plugin-azureblob" width="100%"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/build-passing-6EE7B7?style=flat-square&labelColor=0C1018&color=6EE7B7" alt="build passing"/>
  <img src="https://img.shields.io/badge/license-Apache%202.0-6EE7B7?style=flat-square&labelColor=0C1018&color=6EE7B7" alt="license"/>
  <img src="https://img.shields.io/badge/java-25-2DD4BF?style=flat-square&labelColor=0C1018&color=2DD4BF" alt="java 25"/>
  <img src="https://img.shields.io/badge/version-0.1.1--SNAPSHOT-6EE7B7?style=flat-square&labelColor=0C1018&color=6EE7B7" alt="version"/>
</p>

<br/>

**ktestify-plugin-azureblob** is a [ktestify](https://github.com/ktestify) plugin that adds **Azure Blob Storage** transport support. It implements the `KtestifyPlugin` SPI from [ktestify-core](https://github.com/ktestify/ktestify-core) and ships ready-to-use Cucumber step definitions for uploading and asserting blobs inside your Kafka integration test scenarios.

Drop the JAR into your `ktestify-cucumber` setup and the steps are automatically discovered — no code changes required.

---

## Installation

```xml
<dependency>
  <groupId>io.github.ktestify</groupId>
  <artifactId>ktestify-plugin-azureblob</artifactId>
  <version>0.1.1-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

### With ktestify-cucumber (fat JAR / Docker)

Drop the plugin JAR into the `/workspace/plugins` mount and ktestify-cucumber will load it automatically via `ServiceLoader` at startup:

```bash
docker run --rm \
  -v $(pwd)/features:/workspace/features \
  -v $(pwd)/assets:/workspace/assets \
  -v $(pwd)/plugins:/workspace/plugins \   # ← drop ktestify-plugin-azureblob-*.jar here
  -e AZURE_STORAGE_CONNECTION_STRING="DefaultEndpointsProtocol=..." \
  ghcr.io/ktestify/ktestify-cucumber:latest \
  /workspace/features
```

---

## What It Adds

### Step Definitions

```gherkin
# Upload a blob before the Kafka action
Given blob container
  | containerName  | containerAlias |
  | my-container   | blobs          |

When blob from file is uploaded
  | containerAlias | file           | blobName         |
  | blobs          | payload.json   | orders/input.json|

# Assert a blob was written by the system under test
Then expected blob from file
  | containerAlias | blobName             | file                |
  | blobs          | orders/output.json   | expected-order.json |

And blob should not exist
  | containerAlias | blobName         |
  | blobs          | orders/error.json|
```

### Full Scenario Example

```gherkin
Feature: Order pipeline writes result blob

  Background:
    Given namespace
      | namespace |
      | my-org    |
    Given input topic
      | topicName | topicAlias |
      | orders    | orders-in  |
    Given blob container
      | containerName | containerAlias |
      | results       | results-blob   |
    Given assets directory
      | absolutePath                    |
      | src/test/resources/data/orders  |

  Scenario: Processed order is stored in Azure Blob
    When record from file is sent
      | topicName | file       | recordKey |
      | orders    | order.json | ORD-001   |
    And wait for 5 seconds
    Then expected blob from file
      | containerAlias | blobName              | file                  |
      | results-blob   | ORD-001/result.json   | expected-result.json  |
```

---

## Configuration

The plugin reads its settings from the `ktestify.plugins.azure-blob` HOCON block. All values can be overridden via environment variables.

```hocon
ktestify.plugins.azure-blob {
  # Storage account connection string (takes precedence over SAS / account-key)
  connection-string = ""
  connection-string = ${?AZURE_STORAGE_CONNECTION_STRING}

  # SAS token — used when connection-string is not set
  sas-token = ""
  sas-token = ${?AZURE_STORAGE_SAS_TOKEN}

  # Storage account endpoint (required when using SAS or managed identity)
  endpoint = ""
  endpoint = ${?AZURE_STORAGE_ENDPOINT}

  # Default request timeout for blob operations
  request-timeout = 30s

  # Create containers automatically if they do not exist
  auto-create-containers = true
}
```

### Local development with Azurite

Use the [Azurite](https://github.com/Azure/Azurite) emulator for local and CI testing — no real Azure account needed:

```bash
docker run -p 10000:10000 mcr.microsoft.com/azure-storage/azurite azurite-blob --loose
```

```hocon
ktestify.plugins.azure-blob {
  connection-string = "UseDevelopmentStorage=true"
}
```

---

## Architecture

This plugin implements the `KtestifyPlugin` SPI:

```java
public class AzureBlobPlugin implements KtestifyPlugin {
    @Override public String getId()          { return "azure-blob"; }
    @Override public String getGluePackage() { return "io.github.ktestify.plugin.azureblob.steps"; }

    @Override
    public void initialize(PluginContext ctx) {
        // reads ktestify.plugins.azure-blob from ctx.getConfig()
    }
}
```

It is discovered automatically by `ServiceLoader` — the `META-INF/services/io.github.ktestify.plugin.KtestifyPlugin` descriptor is included in the JAR.

---

## Related

- **[ktestify-core](https://github.com/ktestify/ktestify-core)** — the foundation library and plugin SPI
- **[ktestify-cucumber](https://github.com/ktestify/ktestify-cucumber)** — the BDD runner this plugin extends
- **[docs.ktestify.xyz](https://docs.ktestify.xyz)** — full documentation and configuration reference

---

## Contributing

Contributions are welcome. Please read the contributing guide before opening a pull request.

1. Fork the repository
2. Create a feature branch — `git checkout -b feat/my-feature`
3. Commit with [Conventional Commits](https://www.conventionalcommits.org/) — `git commit -m "feat: add my feature"`
4. Push and open a Pull Request against `main`

---

## License

ktestify-plugin-azureblob is licensed under the [Apache License 2.0](LICENSE).

---

<p align="center">
  <img src="https://raw.githubusercontent.com/ktestify/.github/refs/heads/main/profile/assets/png/ktestify-logo-128.png" width="32" height="32" alt="KTestify"/>
  <br/>
  <sub>Assert the stream. Own the pipeline.</sub>
</p>

