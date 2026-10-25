# tabbie-sdk

The public contracts for [Tabbie](https://github.com/Jelatinone/tabbie): catalogs, addons, distributions,
content, and the changes that install them. Providers implement these contracts; Tabbie carries out what
they describe.

> [!IMPORTANT]
> The API is a work in progress. Expect large breaking changes between releases until 0.1.0.

## Installation

The SDK is published to Maven Central.

Gradle:

```groovy
dependencies {
  implementation 'cat.tabbie:tabbie-sdk:0.1.0'
}
```

Maven:

```xml
<dependency>
  <groupId>cat.tabbie</groupId>
  <artifactId>tabbie-sdk</artifactId>
  <version>0.1.0</version>
</dependency>
```

Modular projects add `requires cat.tabbie.sdk;` to their `module-info.java`.

## Requirements

- Java 25 or later.
- No runtime dependencies. Lombok is used at compile time only; consumers need no Lombok.

## Overview

The SDK describes work; it never writes to a target installation, launches processes, or inspects a
machine. Carrying out its descriptions belongs to the consumer.

| Package | Contents |
|---|---|
| `cat.tabbie.sdk` | Stable identities shared by the other packages |
| `cat.tabbie.sdk.minecraft`, `.minecraft.distribution` | Game versions, labels, distributions and their releases |
| `cat.tabbie.sdk.merchant` | Providers, releases and installers |
| `cat.tabbie.sdk.addon`, `.addon.artifact` | Addon projects, their builds, and the artifacts they carry |
| `cat.tabbie.sdk.album.repository` | Describing, retaining and unpacking content |
| `cat.tabbie.sdk.album.revision` | Images (reversible changes) and intermediates (deferred work) |
| `cat.tabbie.sdk.platform` | Platforms, toolchains, packages and commands |
| `cat.tabbie.sdk.platform.layout` | Addresses and the declared layout of a Cat |
| `cat.tabbie.sdk.api` | Provider-agnostic queries and progress observers |

## Building

```sh
./gradlew build
```

The build formats sources with Spotless, then compiles, tests and generates Javadoc. A Java 25 toolchain
is provisioned automatically if none is installed.

## License

Licensed under the [Apache License, Version 2.0](LICENSE.md).
