# Contributing to Fabricated

Thanks for helping. This guide covers the project layout, how to build and test, the rules the code
follows, and step-by-step instructions for adding a new Minecraft version.

## Project layout

| Path | What it is | Depends on Minecraft? |
|---|---|---|
| `fabric-loader-api/` | Fabric Loader's public API (Apache-2.0, see `NOTICE`) plus clean-room version/predicate/language-adapter implementations | No |
| `core/` | Everything that can be tested without a game: archive safety, `fabric.mod.json`, dependency resolution, remapping, access widener → access transformer, mixin pre-flight, linkage checks, package guard, cache, config, report, Fabric Loader runtime, and the discovery `Pipeline` | No |
| `loader/` | NeoForge discovery services (compiled into each version's jar): candidate/dependency locators, language loader, mod container, interface injection | FML only |
| `mc/common/` | The in-game NeoForge mod (entrypoints, `/fabricated`) and the CI test mods, shared by every version | Yes |
| `mc/<version>/` | One Gradle project per Minecraft version: properties, vendored native Fabric API modules (`src/fabricapi`), the Fabric API plan (`src/fabricated/resources/META-INF/fabricated/fabric-api-plan.json`) and any version-specific code (`src/main/java`) | Yes |
| `ci/` | CI scripts (the real NeoForge server smoke test) | - |
| `scripts/` | Maintenance scripts (importing Fabric API modules) | - |
| `docs/` | Research, architecture and compatibility notes | - |

## Building and testing

Requirements: JDK 21 to run Gradle. JDK 25 is also needed for the 26.x targets; Gradle provisions it
automatically if it is not installed.

```bash
# Minecraft-independent modules and all unit tests (fast, no NeoForge downloads)
./gradlew -Pfabricated.coreOnly=true build

# One Minecraft version: the jar players install, plus the Fabric test mods
./gradlew :mc:26.2:fabricatedJar :mc:26.2:testmods
# -> build/libs/fabricated-<version>+mc26.2.jar and mc/26.2/build/testmods/*.jar

# Everything
./gradlew build

# Real server smoke test (downloads NeoForge's installer; needs network)
ci/smoke-server.sh 26.2 26.2.0.90 /tmp/fabricated-server
```

CI (`.github/workflows/build.yml`) runs the unit tests, then builds every version, boots a real NeoForge
dedicated server with the test mods (and again with the official Fabric API jar), and starts a real NeoForge
client headlessly into a singleplayer world (`ci/smoke-client.sh`; needs Xvfb, and `gh` to fetch
mc-runtime-test). 26.3 is best-effort while NeoForge 26.3 is in beta.

## Code rules

- **Never run mod code during translation.** Everything in `core` reads bytes (ASM `ClassReader`,
  `SafeArchive`); nothing is loaded, defined or reflected.
- **No network access.** Fabricated must never open sockets or URLs at runtime. `NoNetworkTest` enforces
  this on the compiled classes. (Build scripts may download pinned, checksummed inputs.)
- **No new runtime dependencies.** NeoForge already ships ASM, SLF4J and Mixin. Fabricated has its own
  small JSON parser for exactly this reason.
- **Untrusted input is validated.** Every jar goes through `SafeArchive`; every path written goes through
  `TranslationCache`, which stays inside the game directory.
- **Deterministic output.** Generated jars use `DeterministicJarWriter`; maps written to disk are ordered.
- **Fail per mod, not per game.** Anything detectable before mod code runs disables only the affected mod
  and explains why in `logs/fabric-bridge-report.txt`.
- **Don't invent APIs.** Before using a NeoForge or FML class, check it exists on every supported
  version. The sources are public (`neoforged/NeoForge` branches, `neoforged/FancyModLoader` tags).
- **No placeholders.** Don't merge `TODO` stubs or empty implementations. If something is not supported,
  say so in the report and in `docs/COMPATIBILITY.md`.
- Java 21 language level in `core`, `loader` and `fabric-loader-api` (they run on every version). Tabs for
  indentation, as in the existing code.

## Adding a new Minecraft version

Example: adding `26.4` once NeoForge supports it.

1. **Check the facts first.** Find the NeoForge branch and version (`git ls-remote --tags https://github.com/neoforged/NeoForge`),
   its Java version and FML version (`gradle.properties` on the branch), and the matching Fabric API tag
   (`git ls-remote --tags https://github.com/FabricMC/fabric | grep +26.4`). Record them in `docs/RESEARCH.md`.
2. **Check the FML SPI.** Diff `neoforgespi/locating`, `neoforgespi/language`, `neoforgespi/transformation`
   and `fml/jarcontents` against the previous FML version. If they changed, update `loader/` first.
3. **Create the project.** Copy the closest existing directory, e.g. `cp -r mc/26.3 mc/26.4`, then edit
   `mc/26.4/gradle.properties`: `minecraft_version`, `neoforge_version`, `java_version`,
   `neoforge_version_range`, `minecraft_version_range`. Use `namespace=OFFICIAL` for unobfuscated versions.
   `settings.gradle` picks the directory up automatically.
4. **Vendor the native Fabric API modules** from the new Fabric API tag:
   ```bash
   rm -rf mc/26.4/src/fabricapi
   scripts/import-fabric-api.sh 0.170.0+26.4 mc/26.4/src/fabricapi fabric-api-base
   ```
   Repeat for every module marked `native` in the plan. Review the diff.
5. **Update the Fabric API plan** in `mc/26.4/src/fabricated/resources/META-INF/fabricated/fabric-api-plan.json`:
   set `fabricApiVersion` and every module version from Fabric API's `gradle.properties` at that tag. Add new
   modules, defaulting to `passthrough`.
6. **Fix compile errors** in `mc/26.4/src/main/java` (version-specific code) and `mc/common` (shared code).
   If shared code no longer compiles on all versions, move the differing part into each version's
   `src/main/java` rather than adding conditionals.
7. **Add the version to CI** (`.github/workflows/build.yml` matrix) and run the smoke test.
8. **Refine the plan from evidence.** Run the smoke test with the official Fabric API jar in the mods folder
   and read the "Mixin pre-flight" sections of `logs/fabric-bridge-report.txt`. For a module whose mixin
   fails, either add it to that module's `skipMixins` (and describe what is lost in `reason`) or implement
   the module natively.
9. **Update the docs**: the compatibility table in `README.md` and `docs/COMPATIBILITY.md`.

No changes to `core` should be needed for a new version. If they are, keep them version-independent and
add tests.

## Reporting a compatibility result

Run the game with the Fabric mod, then open an issue with:
- the Minecraft, NeoForge and Fabricated versions,
- the mod name, version and download link (no jar attachments),
- `logs/fabric-bridge-report.txt` and `logs/latest.log`.

## Licensing

Fabricated is MIT-licensed. Contributions are accepted under the same license. Do not copy code from
projects with incompatible licenses (for example GPL code such as Sinytra Launchpad). Third-party code under
permissive licenses must keep its headers, and it must be listed in `NOTICE`.
