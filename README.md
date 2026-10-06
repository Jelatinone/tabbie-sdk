# Tabbie SDK

Provider-facing contracts for the Tabbie Minecraft installation manager.

Providers implement these contracts to describe catalogs, releases, addons, distributions and the work
needed to install or run them. The SDK performs no I/O on its own: it hands work back as deferred
`Intermediate` steps, `Image` change sets and `Command` argument lists, and Tabbie executes them.

## Requirements

Java 25 or newer. The SDK is compiled to Java 25 bytecode, so providers need JDK 25 to build against it.

## Coordinates

The SDK is published to Maven Central as `cat.tabbie:tabbie-sdk`.

Gradle:

```groovy
dependencies {
  implementation('cat.tabbie:tabbie-sdk:0.1.0')
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

The jar declares the JPMS module name `cat.tabbie.sdk`.

> **Provider scope (to be decided):** whether a provider declares the SDK `compileOnly` because the
> Tabbie host supplies it at runtime, or bundles it, is settled together with the provider-loading design.
> Until then, use `implementation` for development.

## Versioning

- The SDK follows [Semantic Versioning](https://semver.org). Before 1.0, a minor release may break
  compatibility; a patch release stays source- and binary-compatible.
- Tabbie pins an exact SDK release. It never uses dynamic versions or snapshots.
- An SDK change that Tabbie needs ships as a release first: merge to `production`, tag `v0.x.y`, publish,
  wait for Maven Central to sync (up to about 30 minutes), then bump the version in Tabbie.
- At 1.0, an API compatibility check against the previous release joins CI.

### Hosted versions

Each Tabbie release hosts exactly one SDK version. Providers should target that version.

| Tabbie | SDK |
| --- | --- |
| _unreleased_ | 0.1.0 |

## Building

```sh
./gradlew check
```

`check` runs the unit tests, builds the Javadoc and runs `consumerTest`. The consumer tests in
`src/consumer` compile against the built jar alone, with no Lombok and with `-Xlint:all -Werror`, so they
see the SDK exactly as an external provider does.

To build the full publication without signing or uploading it:

```sh
./gradlew publishAllPublicationsToStagingRepository
```

The artifacts, POM and Gradle module metadata land in `build/staging-repo`.

## Releasing

Releases are published by the [`Release`](.github/workflows/release.yml) workflow when a `v*` tag is
pushed. It signs the publication, packs it into a Central Portal bundle and uploads it through the
[Publisher API](https://central.sonatype.org/publish/publish-portal-api/).

1. Set `version` in `gradle.properties`, then merge to `production`.
2. Tag the merge commit `v<version>` and push the tag. The workflow fails if the tag and version differ.
3. When the workflow reports the deployment as validated, open
   [Deployments](https://central.sonatype.com/publishing/deployments) in the Central Portal, review it and
   press **Publish**. Releases on Central are permanent, so uploads stay `USER_MANAGED` until releases are
   routine.
4. Confirm the release resolves from Maven Central, then add it to [Hosted versions](#hosted-versions)
   when Tabbie adopts it.

To build a signed bundle locally, pass an ASCII-armored key:
`./gradlew centralBundle -PsigningKey="$(cat key.asc)" -PsigningPassword=…`. Without a key,
`centralBundle` fails rather than produce an unsigned bundle.

### One-time setup

1. **Namespace.** Add the DNS TXT record for `tabbie.cat` *before* requesting verification of the
   `cat.tabbie` namespace in the [Central Portal](https://central.sonatype.com), because cached negative
   DNS answers delay verification.
2. **Portal token.** Generate a user token in the Central Portal.
3. **Signing key.** Create a GPG signing key and publish its public key to `keyserver.ubuntu.com` or
   `keys.openpgp.org`, where Central looks it up.
4. **Secrets.** In the repository's `maven-central` environment, add:

   | Secret | Value |
   | --- | --- |
   | `CENTRAL_USERNAME` | Portal user token username |
   | `CENTRAL_PASSWORD` | Portal user token password |
   | `SIGNING_KEY` | ASCII-armored private signing key |
   | `SIGNING_PASSWORD` | Signing key passphrase |

   Restrict the environment's deployments to `v*` tags so only release runs can read them.

## License

[Apache License 2.0](LICENSE)
