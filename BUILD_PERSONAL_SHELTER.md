# Personal Shelter NeoForge build

Target: Minecraft 1.21.1, NeoForge 21.1.215, Java 21, Gradle 8.14.5.

## Build locally (network required on first run)
Install Java 21 and Gradle 8.14.5, then run at repository root:

    gradle :neoforge:build --stacktrace --no-daemon

Look in `neoforge/build/libs/`. The `*-sources.jar` is not installable; use the built runtime mod JAR.

## GitHub Actions
Upload the source files to a repository, then run the workflow named `Personal Shelter NeoForge build` using Actions > Run workflow. Download its build artifact after a successful run. Do not install artifacts if the build failed.

## Local environment limitation
The container had Java 21, but neither Gradle nor gradle-wrapper.jar. Downloading Gradle failed with DNS error: `Could not resolve host: services.gradle.org`. Thus the NeoForge project has NOT been compiled or runtime-tested here.

## Caution
This is a derivative of PersonalDimension and may still use the `personaldimension` mod ID. Do not install alongside the original JAR. Back up your world before testing. Preserve upstream MIT license and attribution.
