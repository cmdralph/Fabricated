# Fabricated for Minecraft Forge

> **This is the `forge` branch:** Fabricated for **Minecraft Forge 61 to 66** (Minecraft 1.21.11, 26.1.2, 26.2 and
> 26.3), on dedicated servers and clients. From 0.6.0-beta Forge is held to the same bar as NeoForge: no launch stop
> caused by Fabricated and no open P0 or P1 finding (the open ones are in the [roadmap](/docs/roadmap.md#open-findings)).
> What works on every loader and version is the [support matrix](/docs/COMPATIBILITY.md#support-matrix), what Forge
> lacks its [*Unsupported on Forge*](/docs/COMPATIBILITY.md#unsupported-on-forge). For NeoForge, see
> [`main`](https://github.com/cmdralph/Fabricated/tree/main); for Forge on Minecraft 1.20.1, see
> [`forge-legacy`](https://github.com/cmdralph/Fabricated/tree/forge-legacy).

Fabricated loads Fabric mods and libraries next to Forge mods. This branch holds the shared engine and the Forge 61 to
66 platform (`main` holds NeoForge's, `forge-legacy` Forge 47's), with the engine merged in from `main`. It builds and
tests the Forge targets, and its releases' `fabricated-forge-*` jars for Forge 61 to 66 come from it.

## Installing (for testing)

1. Install Minecraft Forge for your exact Minecraft version, with the official installer.
2. Download `fabricated-forge-<version>+mc<minecraft>.jar` for that version from the
   [releases](https://github.com/cmdralph/Fabricated/releases).
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
  and the language provider), which Forge 47 shares, with Forge 61 to 66's own below the game in `src/modern`.
- `mc/forge/<version>` and `mc/forge/common*`: the in-game mod and Forge's half of the Fabric test mods in
  `mc/shared`, with the code Forge 47 compiles as it is in `mc/forge-shared`.
- `ci/forge/platform.sh`: how CI's shared harness builds, installs and launches Forge.

CI is main's one workflow and harness ([`CONTRIBUTING.md`](/CONTRIBUTING.md), with the project's layout and rules).

## Documentation

- [`docs/COMPATIBILITY.md`](/docs/COMPATIBILITY.md): the support matrix, and what works on Forge per version and per
  Fabric API module (*Forge*).
- [`docs/roadmap.md`](/docs/roadmap.md): what comes next.
- [`docs/PORTING.md`](/docs/PORTING.md) §4 and §7: the Forge port's design.

## License

GPL-3.0, see [LICENSE](/LICENSE). Third-party material is listed in [NOTICE](/NOTICE).
