# Fabricated for legacy Forge (Minecraft 1.19.2 and 1.20.1)

> **This is the `forge-legacy` branch:** Fabricated for **Minecraft Forge 43 and 47** (Minecraft 1.19.2 and 1.20.1),
> on dedicated servers and clients. **Status: beta (v0.6.0-beta.3)**, held to the same bar as NeoForge in CI; the open
> findings are in the [roadmap](/docs/roadmap.md#open-findings). What works on it is in the
> [support matrix](/docs/COMPATIBILITY.md#support-matrix) and under [*Forge*](/docs/COMPATIBILITY.md#forge) in
> `docs/COMPATIBILITY.md`, what Forge doesn't support yet under
> [*Unsupported on Forge*](/docs/COMPATIBILITY.md#unsupported-on-forge), and what this release changed in its
> [release notes](https://github.com/cmdralph/Fabricated/blob/main/docs/releases/v0.6.0-beta.3.md). For NeoForge, see
> [`main`](https://github.com/cmdralph/Fabricated/tree/main); for Forge on Minecraft 1.21.1, 1.21.11 and 26.x, see
> [`forge`](https://github.com/cmdralph/Fabricated/tree/forge).

Fabricated loads Fabric mods and libraries next to Forge mods. This branch holds the shared engine and the legacy
Forge platform, Forge 43 and 47 (`main` holds NeoForge's, `forge` Forge 52's and 61 to 66's), with the engine merged in
from `main`. It builds and tests the Forge 1.19.2 and 1.20.1 targets, and the releases'
`fabricated-forge-*+mc1.19.2.jar` and `fabricated-forge-*+mc1.20.1.jar` come from it.

| Minecraft | Forge | Java | Fabric mods use |
|---|---|---|---|
| 1.19.2 (beta) | 43.5.2 | 17 | Intermediary names, remapped to Mojang's class names and SRG's member names |
| 1.20.1 | 47.4.28 | 17 | Intermediary names, remapped to Mojang's class names and SRG's member names |

## Installing

1. Install Forge for your Minecraft version with the official installer: Forge 43 for 1.19.2, Forge 47 for 1.20.1.
2. Download `fabricated-forge-<version>+mc<minecraft>.jar` for that version (for this release on 1.20.1,
   `fabricated-forge-0.6.0-beta.3+mc1.20.1.jar`) from the [releases](https://github.com/cmdralph/Fabricated/releases)
   or CurseForge.
3. Check the jar against the release's `SHA256SUMS.txt`.
4. Put the jar in the `mods` folder with your Fabric mods for that Minecraft version. If they need Fabric API, add the
   official Fabric API jar for it (0.77.0 is the last for 1.19.2).

Fabricated never downloads anything and never modifies your mod jars. `logs/fabric-bridge-report.txt` says what loaded
and why anything didn't.

## Building

```bash
./gradlew -Pfabricated.coreOnly=true build                    # unit tests, no Minecraft needed
ci/build.sh forge-legacy 1.20.1                               # the jar and the test mods (or 1.19.2)
# -> build/libs/fabricated-forge-<version>+mc1.20.1.jar and mc/forge-legacy/1.20.1/build/testmods/*.jar
```

The Forge 43 and 47 code is in:
- `loader/forge-shared`: Forge's discovery services (a mod locator, a dependency locator, the transformation service
  and the language provider), which Forge 52 and 61 to 66 share, with Forge 43's and 47's own below the game in
  `src/fml47`.
- `mc/forge-legacy/1.19.2` and `mc/forge-legacy/1.20.1`: each version's in-game mod, reobfuscated to SRG's names, and
  its test mods in Intermediary's names, with the code both versions compile as it is in `mc/forge-legacy/common`, and
  that other Forge versions compile too in `mc/forge-shared`, by version group (`1.19.2-1.21.1`: Forge 43, 47 and 52).

A version here builds with ModDevGradle's `legacyforge` plugin (`mc/platform-forge-legacy.gradle`). CI is `main`'s one
workflow (`.github/workflows/ci.yml`) and harness, with legacy Forge's install and launch in
`ci/forge-legacy/platform.sh`. The project's layout and rules are in [`CONTRIBUTING.md`](/CONTRIBUTING.md).

## License

GPL-3.0, see [LICENSE](/LICENSE). Third-party material is listed in [NOTICE](/NOTICE).
