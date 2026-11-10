# Fabricated for Forge 1.20.1 and 1.21.1

> **This is the `forge-legacy` branch:** Fabricated for **Minecraft Forge 47** (Minecraft 1.20.1) and **Forge 52**
> (Minecraft 1.21.1, new in v0.6.0-beta.2), on dedicated servers and clients. **Status: beta (v0.6.0-beta.2)**, held to
> the same bar as NeoForge in CI; the open findings are in the [roadmap](/docs/roadmap.md#open-findings). What works on
> it is in the [support matrix](/docs/COMPATIBILITY.md#support-matrix) and under [*Forge*](/docs/COMPATIBILITY.md#forge)
> in `docs/COMPATIBILITY.md`, what Forge doesn't support yet under
> [*Unsupported on Forge*](/docs/COMPATIBILITY.md#unsupported-on-forge), and what this release changed in its
> [release notes](https://github.com/cmdralph/Fabricated/blob/main/docs/releases/v0.6.0-beta.2.md). For NeoForge, see
> [`main`](https://github.com/cmdralph/Fabricated/tree/main); for Forge on Minecraft 1.21.11 and 26.x, see
> [`forge`](https://github.com/cmdralph/Fabricated/tree/forge).

Fabricated loads Fabric mods and libraries next to Forge mods. This branch holds the shared engine and the Forge 47 and
Forge 52 platforms (`main` holds NeoForge's, `forge` Forge 61 to 66's), with the engine merged in from `main`. It builds
and tests the Forge 1.20.1 and 1.21.1 targets, and the releases' `fabricated-forge-*+mc1.20.1.jar` and
`fabricated-forge-*+mc1.21.1.jar` come from it.

| Minecraft | Forge | Java | Fabric mods use |
|---|---|---|---|
| 1.20.1 | 47.4.28 | 17 | Intermediary names, remapped to Mojang's class names and SRG's member names |
| 1.21.1 | 52.1.21 | 21 | Intermediary names, remapped to Mojang's |

## Installing

1. Install Forge 47 for Minecraft 1.20.1, or Forge 52 for Minecraft 1.21.1, with the official installer.
2. Download `fabricated-forge-<version>+mc<minecraft>.jar` for that version (for this release,
   `fabricated-forge-0.6.0-beta.2+mc1.20.1.jar` or `fabricated-forge-0.6.0-beta.2+mc1.21.1.jar`) from the
   [releases](https://github.com/cmdralph/Fabricated/releases) or CurseForge. The `fabricated-neoforge-*+mc1.21.1.jar`
   is NeoForge's.
3. Check the jar against the release's `SHA256SUMS.txt`.
4. Put the jar in the `mods` folder with your Fabric mods for that Minecraft version. If they need Fabric API, add the
   official Fabric API jar for it.

Fabricated never downloads anything and never modifies your mod jars. `logs/fabric-bridge-report.txt` says what loaded
and why anything didn't.

## Building

```bash
./gradlew -Pfabricated.coreOnly=true build                    # unit tests, no Minecraft needed
ci/build.sh forge-legacy 1.20.1                               # the jar and the test mods
# -> build/libs/fabricated-forge-<version>+mc1.20.1.jar and mc/forge-legacy/1.20.1/build/testmods/*.jar
ci/build.sh forge-legacy 1.21.1                               # the same for Forge 52
```

The Forge 47 and Forge 52 code is in:
- `loader/forge-shared`: Forge's discovery services (a mod locator, a dependency locator, the transformation service
  and the language provider), which Forge 61 to 66 share, with Forge 47's own below the game in `src/fml47`; Forge 52
  runs Forge 61 to 66's, in `src/modern`.
- `mc/forge-legacy/1.20.1`: the in-game mod, reobfuscated to SRG's names, and the test mod in Intermediary's names,
  with the code Forge 61 to 66 compile as it is in `mc/forge-shared`.
- `mc/forge-legacy/1.21.1`: Forge 52's in-game mod and test mod, most of them from `mc/forge-shared`'s version groups.

Forge 47 builds with ModDevGradle's `legacyforge` plugin (`mc/platform-forge-legacy.gradle`), and Forge 52 with
ForgeGradle, as Forge 61 to 66 do (`mc/platform-forge.gradle`). CI is `main`'s one workflow
(`.github/workflows/ci.yml`) and harness, with Forge 47's and Forge 52's install and launch in
`ci/forge-legacy/platform.sh`. The project's layout and rules are in [`CONTRIBUTING.md`](/CONTRIBUTING.md).

## License

GPL-3.0, see [LICENSE](/LICENSE). Third-party material is listed in [NOTICE](/NOTICE).
