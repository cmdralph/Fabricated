# Contributing to Fabricated

Thanks for helping. This guide covers the project layout, how to build and test, the rules the code
follows, and step-by-step instructions for adding a new Minecraft version.

## Project layout

Each platform is on its own branch, with the same folder structure: `main` holds the shared engine and NeoForge,
`forge` the shared engine and Minecraft Forge 61 to 66, `forge-legacy` the shared engine and Forge 47. Shared changes go
to `main` and reach the platform branches by merge (`scripts/sync-platform-branch.sh`); Forge work goes to its branch
directly. This is `main`'s tree:

| Path | What it is | Depends on Minecraft? |
|---|---|---|
| `fabric-loader-api/` | Fabric Loader's public API (Apache-2.0, see `NOTICE`) plus clean-room version/predicate/language-adapter implementations | No |
| `api/` | Fabricated's public API, what NeoForge and Fabric mods may compile against (`docs/API.md`) | No |
| `core/` | Everything that can be tested without a game: archive safety, `fabric.mod.json`, dependency resolution, remapping, access widener → access transformer, mixin pre-flight, linkage checks, package guard, cache, config, report, Fabric Loader runtime, and the discovery `Pipeline` | No |
| `loader/common/` | Loader code every platform shares: Fabric's class transforms, the vanilla game jar, proposed jars, resource bundles, the mods a platform mod's mods.toml declares | No |
| `loader/neoforge/` | NeoForge's discovery services, compiled into each of its versions' jars: candidate and dependency locators, the mod file reader for Fabric Loader's API, language loader, mod container; in `src/main`, with what FML 10 and later do otherwise than NeoForge 21.1's FML 4 in `src/fml` and `src/modlauncher` (the version's `loader_variant`) | The platform's loader only |
| `loader/forge-shared/` | Both Forge platforms' discovery services, compiled into each Forge version's jar on the Forge branches: mod and dependency locators, transformation service, language provider; in `src/main`, with what Forge 61 to 66 do otherwise than Forge 47 below the game in `src/modern` and `src/fml47` (the version's `loader_variant`) | The platform's loader only |
| `mc/<platform>/<version>/` | One Gradle project per platform and Minecraft version, `:mc:<platform>-<version>`: properties, vendored native Fabric API modules (`src/fabricapi`), the Fabric API plan (`src/fabricated/resources/META-INF/fabricated/fabric-api-plan.json`), version-specific code (`src/main/java`) and the platform's half of the version's test mods | Yes |
| `mc/<platform>/common*/` | Code a platform's versions share, as each version's `shared_sources` lists it: NeoForge's `common` (the in-game mod, the platform's half of the test mods) for every version, `common121` for 1.21.1 and 1.21.11, `common12111` for 1.21.11 and all 26.x versions, `common26` for all 26.x versions, `common26early` for 26.1.2 and 26.2, `common26late` for 26.2 and 26.3 | Yes |
| `mc/shared/` | Sources every platform uses: in `src`, the in-game mod's shared code (`/fabricated`, Mod Menu's API), the broken test mods, stand-ins for other mods' APIs and the build's Fabric API remapper; in `<group>/src`, the Fabric test mods, by the Minecraft versions each group serves (`1.20.1+`, `1.20.1-1.21.1`, `1.21.1+`, `1.21.x`, `1.21.11+`, `26.x`, `26.1-26.2`, `26.2+`, one version's own), which its name says: every version builds the groups that serve it | Yes |
| `mc/mc.gradle`, `mc/platform-<platform>.gradle` | The build shared by every version, and each platform's part of it | - |
| `ci/` | CI's plan (`plan.py`), harness (`smoke-server.sh`, `smoke-client.sh`, `smoke.sh`), runs and checks (`runs.tsv`, `checks.tsv`, `check-log.py`), the benchmark (`bench.py`, its baselines in `bench.tsv`) and the mod corpus lists, every platform's; `ci/<platform>/` holds the platform's adapter (`platform.sh`: its build tasks, how its loader installs and launches) and its stand-ins | - |
| `scripts/` | Maintenance scripts (importing Fabric API modules, the native modules' index, syncing a platform branch with `main`) | - |
| `docs/` | Research, architecture and compatibility notes | - |

## Building and testing

Requirements: JDK 21 to run Gradle. JDK 25 is also needed for the 26.x targets; Gradle provisions it
automatically if it is not installed.

```bash
# Minecraft-independent modules and all unit tests (fast, no NeoForge downloads)
./gradlew -Pfabricated.coreOnly=true build

# One Minecraft version on NeoForge: the jar players install, plus the Fabric test mods
./gradlew :mc:neoforge-26.2:fabricatedJar :mc:neoforge-26.2:testmods
# -> build/libs/fabricated-neoforge-<version>+mc26.2.jar and mc/neoforge/26.2/build/testmods/*.jar

# The Forge platforms build on their branches (forge, forge-legacy), the same way: a build configures the platform
# folders the branch holds.

# Everything on NeoForge
./gradlew build

# A real server with the test mods, checked against ci/checks.tsv (downloads NeoForge's installer; needs network)
ci/smoke-server.sh neoforge 26.2 26.2.0.90 /tmp/fabricated-server

# CI's own scripts and manifests
python3 -B -m unittest discover -s ci -p 'test_*.py'
```

CI is one workflow, `.github/workflows/ci.yml`, the same on every branch; it runs the platform the branch holds. Each
push to a working branch gets the unit tests and its fast job (one touched version booted on a server); a pull request
into `main`, `forge` or `forge-legacy` runs the full matrix for the versions it touches, and a run by hand every version.
The full matrix builds each version, boots real dedicated servers and starts real clients headlessly into a
singleplayer world (HeadlessMC under Xvfb), with the test mods, Fabric API and other people's mods (`ci/runs.tsv`), and
checks each run against `ci/checks.tsv`. A check a platform or version doesn't pass is listed there as not applying,
with the reason. The server run with Fabric API is also the startup and memory benchmark (`ci/bench.py`): a pull request
that takes a metric more than 10% over its baseline in `ci/bench.tsv` records the new value there, with the reason.

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
- Java 17 for the main code of `core`, `api` and `fabric-loader-api` (`release = 17`: they run on every version,
  and on Minecraft 1.20.1's Java 17), Java 21 for `loader` and the tests. Tabs for indentation, as in the existing
  code.

## Adding a new Minecraft version

Example: adding `26.4` once NeoForge supports it.

1. **Check the facts first.** Find the NeoForge branch and version (`git ls-remote --tags https://github.com/neoforged/NeoForge`),
   its Java version and FML version (`gradle.properties` on the branch), and the matching Fabric API tag
   (`git ls-remote --tags https://github.com/FabricMC/fabric | grep +26.4`). Record them in `docs/RESEARCH.md`.
2. **Check the FML SPI.** Diff `neoforgespi/locating`, `neoforgespi/language`, `neoforgespi/transformation`
   and `fml/jarcontents` against the previous FML version. If they changed, update `loader/` first.
3. **Create the project.** Copy the closest existing directory, e.g. `cp -r mc/neoforge/26.3 mc/neoforge/26.4`, then edit
   `mc/neoforge/26.4/gradle.properties`: `minecraft_version`, `neoforge_version`, `java_version`,
   `neoforge_version_range`, `minecraft_version_range`. Use `namespace=OFFICIAL` for unobfuscated versions.
   `settings.gradle` picks the directory up automatically.
4. **Vendor the native Fabric API modules** from the new Fabric API tag:
   ```bash
   rm -rf mc/neoforge/26.4/src/fabricapi
   scripts/import-fabric-api.sh 0.170.0+26.4 mc/neoforge/26.4/src/fabricapi \
       $(sed -n 's/^modules=//p' mc/neoforge/26.3/src/fabricapi/UPSTREAM.properties)
   ```
   Pass every module in one call: `UPSTREAM.properties` records the list of the last import, and the next
   update reads it from there. Review the diff.
5. **Update the Fabric API plan** in `mc/neoforge/26.4/src/fabricated/resources/META-INF/fabricated/fabric-api-plan.json`:
   set `fabricApiVersion` and every module version from Fabric API's `gradle.properties` at that tag. Add new
   modules, defaulting to `passthrough`.
6. **Fix compile errors** in `mc/neoforge/26.4/src/main/java` (version-specific code) and the shared `mc/neoforge/common*` directories.
   If shared code no longer compiles on all versions, move the differing part into each version's
   `src/main/java` rather than adding conditionals.
7. **Add the version to CI**: CI finds the version folder by itself. Mark the checks that don't apply to it in
   `ci/checks.tsv` (with the reason), and run the smoke test.
8. **Refine the plan from evidence.** Run the smoke test with the official Fabric API jar in the mods folder
   and read the "Mixin pre-flight" sections of `logs/fabric-bridge-report.txt`. For a module whose mixin
   fails, either add it to that module's `skipMixins` (and describe what is lost in `reason`) or implement
   the module natively. A pass-through module that requires an unsupported one only for code NeoForge never runs
   can load without it: list it in `dropDependencies` and say why in `reason`.
9. **Update the docs**: the compatibility table in `README.md` and `docs/COMPATIBILITY.md`.

No changes to `core` should be needed for a new version. If they are, keep them version-independent and
add tests.

## Reporting a compatibility result

Run the game with the Fabric mod, then open an issue with:
- the Minecraft, NeoForge and Fabricated versions,
- the mod name, version and download link (no jar attachments),
- `logs/fabric-bridge-report.txt` and `logs/latest.log`.

## Releasing

A release is cut by hand with the Release workflow (`.github/workflows/release.yml`), run from `main`. It builds all ten
jars from three commits it pins when it starts: NeoForge's five from `main`, Forge 61 to 66's four from `forge` and
Forge 47's from `forge-legacy` (their heads by default), each with the build its platform's CI uses, from an empty
Gradle cache. A failed build is tried once more (a platform's maven can time out), and every build runs to the end.

1. Merge the release's work into `main`, then sync `forge` and `forge-legacy` with it
   (`scripts/sync-platform-branch.sh`, a pull request each), all three with their CI green, and check that
   `fabricated_version` in `gradle.properties` is the release's version on each.
2. Write the notes as `docs/releases/<tag>.md` (for example `docs/releases/v0.4.0-alpha.md`) and merge them into
   `main`. They become the release's text, so links are full URLs (a relative link resolves against the release page).
   The workflow appends the commits the jars were built from and the jars' SHA-256 checksums.
3. Run the workflow with mode `build`, and check the jars and checksums in the run's summary.
4. Run it again with mode `draft` or `publish`. A tag with a suffix (`-alpha`) makes a pre-release. GitHub creates the
   tag at `main`'s pinned commit when the release is published; no tag is pushed from a working copy.

## Licensing

Fabricated is licensed under GPL-3.0. Contributions are accepted under the same license. Only copy code whose
license is compatible with GPL-3.0 (for example Apache-2.0, MIT or CC0); it must keep its headers and be listed in
`NOTICE`. Fabricated remains a clean-room design: do not copy code from Sinytra Connector, Launchpad, Adapter,
Forgified Fabric API or Forgified Fabric Loader, whatever their license.
