# Fabricated for Forge 1.20.1

> **This is the `forge-legacy` branch:** Fabricated for **Minecraft Forge 47.4** (Minecraft 1.20.1), on dedicated
> servers and clients, held to the same bar as NeoForge. What works on it, and what Forge doesn't support yet, is in
> the [support matrix](/docs/COMPATIBILITY.md#support-matrix) and under [*Forge*](/docs/COMPATIBILITY.md#forge) in
> `docs/COMPATIBILITY.md`. For NeoForge, see [`main`](https://github.com/cmdralph/Fabricated/tree/main); for Forge on
> Minecraft 1.21.11 and 26.x, see [`forge`](https://github.com/cmdralph/Fabricated/tree/forge).

Fabricated loads Fabric mods and libraries next to Forge mods. This branch holds the shared engine and the Forge 47
platform (`main` holds NeoForge's, `forge` Forge 61 to 66's), with the engine merged in from `main`. It builds and
tests the Forge 1.20.1 target, and its releases' `fabricated-forge-*+mc1.20.1.jar` comes from it.

| Minecraft | Forge | Java | Fabric mods use |
|---|---|---|---|
| 1.20.1 | 47.4.28 | 17 | Intermediary names, remapped to Mojang's class names and SRG's member names |

## Installing (for testing)

1. Install Forge 47.4 for Minecraft 1.20.1, with the official installer.
2. Download `fabricated-forge-<version>+mc1.20.1.jar` from the
   [releases](https://github.com/cmdralph/Fabricated/releases).
3. Check the jar against the release's `SHA256SUMS.txt`.
4. Put the jar in the `mods` folder with your Fabric mods for 1.20.1. If they need Fabric API, add the official
   Fabric API jar for 1.20.1.

Fabricated never downloads anything and never modifies your mod jars. `logs/fabric-bridge-report.txt` says what loaded
and why anything didn't.

## Building

```bash
./gradlew -Pfabricated.coreOnly=true build                    # unit tests, no Minecraft needed
ci/build.sh forge-legacy 1.20.1                               # the jar and the test mods
# -> build/libs/fabricated-forge-<version>+mc1.20.1.jar and mc/forge-legacy/1.20.1/build/testmods/*.jar
```

The Forge 47 code is in:
- `loader/forge-legacy`: Forge 47's discovery services, with the language provider and the transformation service in
  `loader/forge-shared`, which Forge 61 to 66 share.
- `mc/forge-legacy/1.20.1`: the in-game mod, reobfuscated to SRG's names, and the test mod in Intermediary's names,
  with the code Forge 61 to 66 compile as it is in `mc/forge-shared`.

The build uses ModDevGradle's `legacyforge` plugin (`mc/platform-forge-legacy.gradle`). CI is `main`'s one workflow
(`.github/workflows/ci.yml`) and harness, with Forge 47's install and launch in `ci/forge-legacy/platform.sh`. The
project's layout and rules are in [`CONTRIBUTING.md`](/CONTRIBUTING.md).

## License

GPL-3.0, see [LICENSE](/LICENSE). Third-party material is listed in [NOTICE](/NOTICE).
