import io.ktor.plugin.features.*

object Versions {
    const val HOPLITE_VERSION = "2.9.0"
}

plugins {
    id("waltid.ktorbackend")
    id("waltid.ktordocker")
}

group = "id.walt"

dependencies {
    api(project(":waltid-services:waltid-service-commons"))

    /* -- KTOR -- */

    // Ktor server
    implementation(identityLibs.ktor.server.core)
    implementation(identityLibs.ktor.server.auth)
    implementation(identityLibs.ktor.server.sessions)
    implementation(identityLibs.ktor.server.authjwt)
    implementation(identityLibs.ktor.server.auto.head.response)
    implementation(identityLibs.ktor.server.double.receive)
    implementation(identityLibs.ktor.server.host.common)
    implementation(identityLibs.ktor.server.status.pages)
    implementation(identityLibs.ktor.server.compression)
    implementation(identityLibs.ktor.server.cors)
    implementation(identityLibs.ktor.server.forwarded.header)
    implementation(identityLibs.ktor.server.call.logging)
    implementation(identityLibs.ktor.server.call.id)
    implementation(identityLibs.ktor.server.content.negotiation)
    implementation(identityLibs.ktor.server.cio)

    // Ktor client
    implementation(identityLibs.ktor.client.core)
    implementation(identityLibs.ktor.client.serialization)
    implementation(identityLibs.ktor.client.content.negotiation)
    implementation(identityLibs.ktor.client.json)
    implementation(identityLibs.ktor.client.java)
    implementation(identityLibs.ktor.client.logging)


    /* -- Kotlin -- */

    // Kotlinx.serialization
    implementation(identityLibs.ktor.serialization.kotlinx.json)

    // Date
    implementation(identityLibs.kotlinx.datetime)

    // Coroutines
    implementation(identityLibs.kotlinx.coroutines.core)

    /* -- Misc --*/

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:${Versions.HOPLITE_VERSION}")
    implementation("com.sksamuel.hoplite:hoplite-hocon:${Versions.HOPLITE_VERSION}")

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)
    implementation(identityLibs.slf4j.julbridge)
    implementation(identityLibs.klogging)
    implementation(identityLibs.slf4j.klogging)

    // Test
    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.test)

    // OIDC
    api(project(":waltid-libraries:protocols:waltid-openid4vc"))

    // walt.id
    api(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation(project(":waltid-libraries:crypto:waltid-x509"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-aws"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-azure"))

    api(project(":waltid-libraries:waltid-did"))

    api(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
    api(project(":waltid-libraries:credentials:waltid-verification-policies"))
    api(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
    api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))

    // crypto
    implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
    implementation(identityLibs.nimbus.jose.jwt)
    // Bouncy Castle
    implementation(identityLibs.bouncycastle.prov)
    implementation(identityLibs.bouncycastle.pkix)

    // Multiplatform / Hashes
    testImplementation(identityLibs.kotlincrypto.hash.sha2)
}

application {
    mainClass.set("id.walt.issuer.MainKt")
}

buildConfig {
    packageName("id.walt.issuer")
}

good morning, here is the yaml for docker.io. I base deployment on git tags. I would like the version of the docker image to be based upon that...name: Publish patched issuer-api from branch

on:
  workflow_dispatch:
    inputs:
      image_tag:
        description: 'Tag to publish (e.g. 0.21.3-mdl-patch)'
        required: true
        default: 'v0.21.3-mdl-patch-v2'

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Publish image
        env:
          DOCKER_USERNAME: ${{ secrets.DOCKER_USERNAME }}
          DOCKER_PASSWORD: ${{ secrets.DOCKER_PASSWORD }}
        run: ./gradlew :waltid-services:waltid-issuer-api:publishImage -PimageTag=${{ github.event.inputs.image_tag }}Show moreDiagnosing why imageTag parameter isn't overriding Gradle versionDiagnosing why imageTag parameter isn't overriding Gradle versionThis confirms it. The Ktor Gradle plugin's publishImage task tags the image via the ktor.docker.imageTag extension property (a Gradle Property<String>), set inside a docker { } block in build.gradle.kts — not from an arbitrary -P CLI flag. Unless waltid-issuer-api/build.gradle.kts explicitly does something like:
kotlinktor {
    docker {
        imageTag.set(project.findProperty("imageTag") as String? ?: project.version.toString())
    }
}
your -PimageTag=... CLI arg is just an unused, orphaned Gradle property — Jib/Ktor never reads it, so it silently falls back to whatever imageTag/project.version is hardcoded to in that file (explains "latest" + "1.0.0-snapshot" every time, regardless of your input).
Fix — two parts:
1. Wire the CLI property into the Ktor docker block. In waltid-services/waltid-issuer-api/build.gradle.kts, find the ktor { docker { ... } } block and check if imageTag is set. If it's hardcoded or missing, change it to read the Gradle property:
kotlinktor {
    docker {
        localImageName.set("issuer-api")
        imageTag.set(
            (project.findProperty("imageTag") as String?) ?: "latest"
        )
    }
}
2. Base the tag on your git tag, not a manual dropdown default. Since you said you want the image version driven by git tags, change the workflow to derive image_tag from the pushed tag instead of (or as a fallback to) the workflow_dispatch text input:
yamlname: Publish patched issuer-api from branch

on:
  push:
    tags:
      - 'v*'
  workflow_dispatch:
    inputs:
      image_tag:
        description: 'Tag to publish (e.g. 0.21.3-mdl-patch)'
        required: false

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Resolve image tag
        id: vars
        run: |
          if [ -n "${{ github.event.inputs.image_tag }}" ]; then
            echo "tag=${{ github.event.inputs.image_tag }}" >> "$GITHUB_OUTPUT"
          else
            echo "tag=${GITHUB_REF_NAME}" >> "$GITHUB_OUTPUT"
          fi
      - name: Publish image
        env:
          DOCKER_USERNAME: ${{ secrets.DOCKER_USERNAME }}
          DOCKER_PASSWORD: ${{ secrets.DOCKER_PASSWORD }}
        run: ./gradlew :waltid-services:waltid-issuer-api:publishImage -PimageTag=${{ steps.vars.outputs.tag }}
That way pushing v0.21.3-mdl-patch-v3 auto-triggers a build tagged with that exact string — you don't have to remember to bump the manual input, and it can't silently reuse a stale tag. But the CLI flag is only half the fix — you still need to confirm build.gradle.kts actually consumes imageTag. Paste that file (or the ktor { docker {...} } block specifically) and I'll tell you exactly what to change.that did not tag the image in docker. hub.Investigated Docker tagging failure and identified missing configuration detailsInvestigated Docker tagging failure and identified missing configuration detailsNeed to see what's actually happening rather than guess again. Can you paste:

The ktor { docker { ... } } block from waltid-services/waltid-issuer-api/build.gradle.kts (or grep for imageTag in that file if it's not in an obvious block)
The Actions run log output for the publishImage step — specifically the line(s) where it prints what image/tag it's pushing (Jib usually logs Built and pushed image as docker.io/...:<tag>)
What you actually see in Docker Hub right now (tag name, timestamp) vs. what you expected

Also — did you already apply the imageTag.set(project.findProperty("imageTag") as String? ?: ...) change to build.gradle.kts, or is that still outstanding? If it's still outstanding, that's almost certainly still the whole problem: the -PimageTag= flag on the CLI does nothing until something in the build script reads it.//  Uncomment the following to run the license report
// ./gradlew -p waltid-identity aggregateDependencyNotices --no-configuration-cache
//plugins {
//    id("waltid.licensereport")
//}
//
//subprojects {
//    if (subprojects.isEmpty()) {
//        apply(plugin = "waltid.licensereport")
//    }
//}

allprojects {
    version = "1.0.0-SNAPSHOT"

    repositories {
        google()
        mavenCentral()
        maven("https://maven.waltid.dev/releases")
        maven("https://maven.waltid.dev/snapshots")
    }
}Show moreIdentified hardcoded version causing Docker image tagging issuesIdentified hardcoded version causing Docker image tagging issuesFound it. allprojects { version = "1.0.0-SNAPSHOT" } in the root build.gradle.kts hardcodes the version for every subproject, issuer-api included. That's exactly your "1.0.0-snapshot" tag — Docker/Jib lowercases it. This confirms the theory: since this is a literal assignment (not reading a property with a fallback), your -PimageTag=... CLI flag never had a path into it unless waltid-issuer-api's own build.gradle.kts explicitly wires ktor.docker.imageTag to that property, independent of project.version.
I don't want to touch this root file — minimal-patch discipline, and it's shared across every subproject in the fork. The fix belongs in waltid-services/waltid-issuer-api/build.gradle.kts instead, overriding the Ktor docker tag directly so it ignores project.version entirely:
kotlinktor {
    docker {
        imageTag.set(
            (project.findProperty("imageTag") as String?) ?: project.version.toString()
        )
    }
}
Can you paste the actual waltid-issuer-api/build.gradle.kts (or just its plugins {} and any existing ktor { } block, if one exists)? I need to see whether:

there's no ktor { docker { } } block at all (likely, if it's inheriting Ktor plugin defaults), in which case we add one, or
there's one but it's missing the imageTag.set(...) line, in which case we add just that line.

Either way, that's the missing wire between your -PimageTag= flag and the actual Jib tag.import io.ktor.plugin.features.*

object Versions {
    const val HOPLITE_VERSION = "2.9.0"
}

plugins {
    id("waltid.ktorbackend")
    id("waltid.ktordocker")
}

group = "id.walt"

dependencies {
    api(project(":waltid-services:waltid-service-commons"))

    /* -- KTOR -- */

    // Ktor server
    implementation(identityLibs.ktor.server.core)
    implementation(identityLibs.ktor.server.auth)
    implementation(identityLibs.ktor.server.sessions)
    implementation(identityLibs.ktor.server.authjwt)
    implementation(identityLibs.ktor.server.auto.head.response)
    implementation(identityLibs.ktor.server.double.receive)
    implementation(identityLibs.ktor.server.host.common)
    implementation(identityLibs.ktor.server.status.pages)
    implementation(identityLibs.ktor.server.compression)
    implementation(identityLibs.ktor.server.cors)
    implementation(identityLibs.ktor.server.forwarded.header)
    implementation(identityLibs.ktor.server.call.logging)
    implementation(identityLibs.ktor.server.call.id)
    implementation(identityLibs.ktor.server.content.negotiation)
    implementation(identityLibs.ktor.server.cio)

    // Ktor client
    implementation(identityLibs.ktor.client.core)
    implementation(identityLibs.ktor.client.serialization)
    implementation(identityLibs.ktor.client.content.negotiation)
    implementation(identityLibs.ktor.client.json)
    implementation(identityLibs.ktor.client.java)
    implementation(identityLibs.ktor.client.logging)


    /* -- Kotlin -- */

    // Kotlinx.serialization
    implementation(identityLibs.ktor.serialization.kotlinx.json)

    // Date
    implementation(identityLibs.kotlinx.datetime)

    // Coroutines
    implementation(identityLibs.kotlinx.coroutines.core)

    /* -- Misc --*/

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:${Versions.HOPLITE_VERSION}")
    implementation("com.sksamuel.hoplite:hoplite-hocon:${Versions.HOPLITE_VERSION}")

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)
    implementation(identityLibs.slf4j.julbridge)
    implementation(identityLibs.klogging)
    implementation(identityLibs.slf4j.klogging)

    // Test
    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.test)

    // OIDC
    api(project(":waltid-libraries:protocols:waltid-openid4vc"))

    // walt.id
    api(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation(project(":waltid-libraries:crypto:waltid-x509"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-aws"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-azure"))

    api(project(":waltid-libraries:waltid-did"))

    api(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
    api(project(":waltid-libraries:credentials:waltid-verification-policies"))
    api(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
    api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))

    // crypto
    implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")
    implementation(identityLibs.nimbus.jose.jwt)
    // Bouncy Castle
    implementation(identityLibs.bouncycastle.prov)
    implementation(identityLibs.bouncycastle.pkix)

    // Multiplatform / Hashes
    testImplementation(identityLibs.kotlincrypto.hash.sha2)
}

application {
    mainClass.set("id.walt.issuer.MainKt")
}

buildConfig {
    packageName("id.walt.issuer")
}

ktor {
    docker {
        imageTag.set(
            (project.findProperty("imageTag") as String?) ?: project.version.toString()
        )
        portMappings.set(
            listOf(
                DockerPortMapping(7002, 7002, DockerPortMappingProtocol.TCP)
            )
        )
    }
}
