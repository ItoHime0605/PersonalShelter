# Personal Shelter — Stage 11

## Changes
- `ShelterPermitTransaction`: added a server-authoritative overload that resolves the shelter's owner from `ModDimensions.ownerOf(shelter.dimension())`, comparing the UUID to the acting player. The former overload accepting a caller-supplied `verifiedOwner` flag is now private to prevent future code from accidentally bypassing authorization.
- Added `tests/ShelterSquareExpansionTest.java`: independently verifies the exact number of newly generated square-ring blocks for every upgrade and all four corners.

## Validation
- Pure-Java geometry test: PASS (4 upgrade steps).
- **Full NeoForge/Gradle build: NOT RUN**. This is source code, not an installable mod.
- Server-side single-slot menu and packet, world border event coverage, and crash-safe expansion transaction remain unimplemented.

## Build prerequisites
- Minecraft 1.21.1, NeoForge dependencies, Java 21, Gradle wrapper distribution and cached/downloadable dependencies.
- Run `./gradlew :neoforge:build` in the project root in an environment with dependency access.
