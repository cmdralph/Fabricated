# Fabricated for Minecraft Forge

> **This is the `forge` branch:** Fabricated for **Minecraft Forge 61 to 66** (Minecraft 1.21.11, 26.1.2, 26.2 and
> 26.3), on dedicated servers and clients. **Status: beta (v0.6.0-beta.2)**, held to the same bar as NeoForge in CI;
> the open findings are in the [roadmap](/docs/roadmap.md#open-findings). What works on every loader and version is the
> [support matrix](/docs/COMPATIBILITY.md#support-matrix), what Forge lacks its
> [*Unsupported on Forge*](/docs/COMPATIBILITY.md#unsupported-on-forge), and what this release changed its
> [release notes](https://github.com/cmdralph/Fabricated/blob/main/docs/releases/v0.6.0-beta.2.md). For NeoForge, see
> [`main`](https://github.com/cmdralph/Fabricated/tree/main); for Forge 47 and Forge 52 (Minecraft 1.20.1 and 1.21.1),
> see [`forge-legacy`](https://github.com/cmdralph/Fabricated/tree/forge-legacy).

Fabricated loads Fabric mods and libraries next to Forge mods. This branch holds the shared engine and the Forge 61 to
66 platform (`main` holds NeoForge's, `forge-legacy` Forge 47's and Forge 52's), with the engine merged in from `main`.
It builds and tests the Forge 61 to 66 targets, and the releases' `fabricated-forge-*` jars for them come from it.

| Minecraft | Forge | Java | Fabric mods use |
|---|---|---|---|
| 1.21.11 | 61.2.4 | 21 | Intermediary names, remapped to Mojang's |
| 26.1.2 | 64.1.5 | 25 | Mojang's names (no remapping) |
| 26.2 | 65.1.5 | 25 | Mojang's names (no remapping) |
| 26.3 | 66.1.0 | 25 | Mojang's names (no remapping) |

## Installing

1. Install Minecraft Forge for your exact Minecraft version, with the official installer.
2. Download `fabricated-forge-<version>+mc<minecraft>.jar` for that version (for this release, such as
   `fabricated-forge-0.6.0-beta.2+mc26.3.jar`) from the [releases](https://github.com/cmdralph/Fabricated/releases) or
   CurseForge.
3. Check the jar against the release's `SHA256SUMS.txt`.
4. Put the jar in the `mods` folder with your Fabric mods. If they need Fabric API, add the official Fabric API jar
   for your version.

On 1.21.11, Fabricated remaps Fabric mods from Intermediary names, using the Mojang mappings that Forge's installer
leaves. Fabricated never downloads anything and never modifies your mod jars. `logs/fabric-bridge-report.txt` says
what loaded and why anything didn't.

## Building

```bash
./gradlew -Pfabricated.coreOnly=true build                    # unit tests, no Minecraft needed
ci/build.sh forge 26.2                                        # the jar and the Fabric test mods
# -> build/libs/fabricated-forge-<version>+mc26.2.jar and mc/forge/26.2/build/testmods/*.jar
ci/smoke.sh forge 26.2 server                                 # the version's server runs, checked
```

The Forge code is in:
- `loader/forge-shared`: Forge's discovery services (a mod locator, a dependency locator, the transformation service
  and the language provider), which Forge 47 and 52 share, with what Forge 52 and 61 to 66 do otherwise than Forge 47
  below the game in `src/modern`.
- `mc/forge/<version>` and `mc/forge/common*`: the in-game mod and Forge's half of the Fabric test mods in
  `mc/shared`, with the code Forge 47 and 52 compile as they are in `mc/forge-shared`.
- `ci/forge/platform.sh`: how CI's shared harness builds, installs and launches Forge.

CI is main's one workflow and harness ([`CONTRIBUTING.md`](/CONTRIBUTING.md), with the project's layout and rules).

## Documentation

- [`docs/COMPATIBILITY.md`](/docs/COMPATIBILITY.md): the support matrix, and what works on Forge per version and per
  Fabric API module (*Forge*).
- [`docs/roadmap.md`](/docs/roadmap.md): what comes next.
- [`docs/PORTING.md`](/docs/PORTING.md) §4 and §7: the Forge port's design.

## License

GPL-3.0, see [LICENSE](/LICENSE). Third-party material is listed in [NOTICE](/NOTICE).
