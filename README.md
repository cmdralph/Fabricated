# Fabricated

Fabricated is a NeoForge mod that loads **Fabric mods and libraries on NeoForge**, for Minecraft
**1.21.1, 1.21.11, 26.1.2, 26.2 and 26.3**. Put Fabricated and your Fabric mods in the `mods` folder, and they load
next to your NeoForge mods.

It's built to be:

- **Lightweight.** No dependencies beyond what NeoForge already ships. Translation results are cached, so
  later launches re-check memoised file hashes instead of translating again (NeoForge mods' nested jars are still
  read each launch). Fabricated itself adds nothing to the game.
- **Robust.** Broken Fabric mods are disabled and not allowed to crash your game, whenever that can be decided
  before their code runs. Everything is explained in `logs/fabric-bridge-report.txt`.
- **Safe.** Mod jars are treated as untrusted input. Fabricated never runs mod code while translating, never
  modifies your mod jars, never touches the network, and has no telemetry.

> **Status: alpha (v0.5.0-alpha).** Nearly every Fabric API module
> loads, but many Fabric mods still patch code that NeoForge changed and are disabled before they run. Read
> [What works today](#what-works-today) before relying on it.

**Platforms and branches.** NeoForge is the supported platform, and this README is about it. Builds for Minecraft
Forge are experimental previews, for dedicated servers and for clients that run Fabric mods' client entrypoints, each
with a README of its own on its branch:

| Branch | Platform | Minecraft | Jars |
|---|---|---|---|
| [`main`](https://github.com/cmdralph/Fabricated/tree/main) | NeoForge | 1.21.1, 1.21.11, 26.1.2, 26.2, 26.3 | `fabricated-<version>+mc<minecraft>.jar` |
| [`forge`](https://github.com/cmdralph/Fabricated/tree/forge) | Minecraft Forge 61 to 66 (experimental) | 1.21.11, 26.1.2, 26.2, 26.3 | `fabricated-forge-<version>+mc<minecraft>.jar` |
| [`forge-legacy`](https://github.com/cmdralph/Fabricated/tree/forge-legacy) | Forge 47 (experimental) | 1.20.1 | `fabricated-forge-<version>+mc1.20.1.jar` |

Every branch holds the same code, with one folder per platform and version. A branch differs in the platform its CI
builds and in its README.

---

## What works today

Every version below is tested in CI with Fabric test mods. Tests cover Fabric entrypoints (main,
server and client), the Fabric Loader API, registering content, the native Fabric API modules, access
wideners, mixins, and the isolation of broken mods (for example a missing dependency or an impossible mixin).

| Minecraft | NeoForge | Java | Fabric mods use | Status |
|---|---|---|---|---|
| 1.21.1 | 21.1.265 | 21 | Intermediary names (remapped at load) | ✅ test mods pass; Fabric renderer API models aren't drawn in chunk sections yet |
| 1.21.11 | 21.11.45 | 21 | Intermediary names (remapped at load) | ✅ test mods pass |
| 26.1.2 | 26.1.2.116 | 25 | Mojang names (no remapping) | ✅ test mods pass |
| 26.2 | 26.2.0.90 | 25 | Mojang names (no remapping) | ✅ test mods pass |
| 26.3 | 26.3.0.95 | 25 | Mojang names (no remapping) | ✅ test mods pass |

**Fabric API** is the big caveat. Most Fabric mods need it, and many of its modules patch Minecraft code
that NeoForge has already changed. Fabricated handles this per module:

- **Native**: Fabricated ships the module itself. Today that is `fabric-api-base`,
  `fabric-lifecycle-events-v1`, `fabric-networking-api-v1` (login queries excepted),
  `fabric-resource-loader-v1` (`fabric-resource-loader-v0` on 1.21.1), `fabric-registry-sync-v0`,
  `fabric-entity-events-v1`, `fabric-object-builder-api-v1`, `fabric-item-api-v1`, `fabric-block-api-v1`,
  `fabric-events-interaction-v0`, `fabric-loot-api-v3`, `fabric-content-registries-v0`,
  `fabric-transfer-api-v1`, `fabric-api-lookup-api-v1`, `fabric-menu-api-v1` (`fabric-screen-handler-api-v1`
  on 1.21.1 and 1.21.11), `fabric-biome-api-v1`, `fabric-crash-report-info-v1`, and on 1.21.1 `fabric-particles-v1`.
- **Pass-through**: the module is loaded from the official Fabric API jar you install. Fabricated checks
  every mixin against NeoForge's code before loading it ("mixin pre-flight"). Modules whose mixins can't
  apply are disabled, along with every mod that requires them, with an explanation.
- **Unsupported**: the development-only game test modules. Data generation passes through: mods' runtime code
  uses its classes, and it generates nothing unless the `fabric-api.datagen` property is set.

Fabric API modules that can't work are disabled, and the rest load. So Fabric mods that only require Fabric Loader API, 
`fabric-api-base` or pass-through modules that survive pre-flight work today, including mods that add 
reload listeners, built-in packs, their own registries, items and blocks using Fabric's item and block APIs, 
entities with default attributes, block entity types, data attachments or Fabric's interaction events. 
Loot table events, content registries and Fabric's transfer API work too. Per-module details are in [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

Fabric mods can also reach and integrate with NeoForge mods: a Fabric mod's plugins for JEI, REI, Jade, WTHIT and
Simple Voice Chat are found by those mods' NeoForge builds (EMI's are converted too, untested in CI), its config
screen opens from NeoForge's mod list, and its mixins into an installed NeoForge mod are checked before the game
starts.

## What it does not support

- **Fabric API modules that NeoForge conflicts with**, until Fabricated implements them natively (see above).
- **The Fabric builds of renderer mods** (Sodium, Iris, ...), by default. Use their NeoForge builds:
     26.x Sodium for NeoForge draws Fabric block models through its own Fabric renderer, and Iris runs on top (see
     [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md)). Running their Fabric builds is a work in progress
     ([docs/roadmap.md](docs/roadmap.md), rendering-pipeline track); `loadUnsupported` loads them for testing.
  
- **Mods that use Fabric Loader internals** (`net.fabricmc.loader.impl.*`). Only the public Fabric Loader
  API is provided.
- **Fabric mods built for another Minecraft version.** A Fabric mod for 1.21.11 won't run on 26.x and vice
  versa, just as on Fabric.
- **Mixins into library classes** (DataFixerUpper, Brigadier, the JDK). NeoForge loads those without mixin
  support. Pre-flight detects this: a mixin nothing outside it calls into is left out and the mod loads without that
  feature; otherwise the mod is disabled instead of letting it misbehave.
- **Multiplayer client testing.** CI starts a client into a singleplayer world, which covers the client and the
  integrated server, but not a client joining a separate dedicated server.

## Installing

1. Install NeoForge for your Minecraft version with the official installer (client or server).
2. Download the Fabricated jar for **your exact Minecraft version**: `fabricated-<version>+mc<minecraft>.jar`.
3. Put it in the `mods` folder.
4. Put your Fabric mods in the same `mods` folder. If they need Fabric API, add the **official Fabric API
   jar** for your Minecraft version. Fabricated doesn't include or download it, and only loads the parts
   that work on NeoForge.
5. Start the game. Open `logs/fabric-bridge-report.txt` to see what loaded and why anything didn't.

On **1.21.1 and 1.21.11**, Fabric mods use Fabric's Intermediary names, so Fabricated remaps them to Mojang's
names on first load. It needs Mojang's mapping file, which the NeoForge installer already downloads. Nothing
is fetched at runtime.

If a mod exists for both loaders, Fabricated uses the NeoForge version: NeoForge cannot load two mods
with the same id. Remove the NeoForge jar to use the Fabric one.

## Troubleshooting

**Where do I look first?** `logs/fabric-bridge-report.txt`. It is rewritten on every launch, and `/fabricated
report` regenerates it. It lists every Fabric mod with its status (loaded, disabled, rejected, provided
natively, skipped), the reason, and a suggested fix. It also has the Fabric API plan, mixin pre-flight
results, access-widener notes and timings. In game, `/fabricated` prints a one-line summary and `/fabricated disabled`
lists the Fabric mods that didn't load, with why. `/fabricated why <mod>` tells one mod's outcome and the suggested
fix, and `/fabricated disable <mod>` (or `enable <mod>`) switches a mod off (or on) in `config/fabricated.json` from
the next launch, without editing the file by hand (comments in the file aren't kept). On a server, all but the summary work from the console only.

| You see | Meaning | What to do |
|---|---|---|
| "requires X, which is not installed" | A dependency is missing | Install X for the same Minecraft version. If X is a Fabric API module, check the "Disabled" section: it may have been disabled itself. |
| "Mixin pre-flight failures" | A mixin targets code NeoForge changed | Usually needs the mod (or Fabricated) to be updated. If the feature is optional, skip that mixin (see below). |
| "references code that does not exist at runtime" | The mod calls a Minecraft method NeoForge changed or removed | Fabric API modules with this problem are disabled, unless Fabricated loads the module without the one the code needs (the code then never runs). Other mods still load, with a warning, and may crash when that code runs (`strictLinkage: true` disables them too). The mod needs an update. |
| An error screen naming a Fabric entrypoint | A mod's initializer threw an exception | Update or remove the mod, or set `"onEntrypointFailure": "disable"` to keep loading without it. |
| "packages … clash" | Two mods ship the same Java package (not allowed on NeoForge) | Remove one. Identical duplicate libraries are de-duplicated automatically. |

### Configuration: `config/fabricated.json`

The file is created with defaults on first launch. Unknown or invalid values fall back to the defaults with a
warning in the report, so a bad config never crashes the game.

```json
{
  "schema": 1,
  "onEntrypointFailure": "error",
  "strictDependencies": false,
  "strictLinkage": false,
  "cache": { "enabled": true, "verifyIntegrity": true, "maxUnusedLaunches": 5 },
  "limits": { "maxJarBytes": 268435456, "maxEntryBytes": 134217728, "maxTotalUncompressedBytes": 536870912,
              "maxEntries": 65536, "maxCompressionRatio": 100, "maxNestingDepth": 3 },
  "mods": {
    "examplemod": {
      "disabled": false,
      "skipMixins": ["examplemod.mixins.json:client.FooMixin"],
      "skipEntrypoints": ["client"],
      "aliases": ["example"],
      "ignorePreflight": false,
      "ignoreHints": false,
      "loadUnsupported": false
    }
  },
  "mojangMappingsPath": null
}
```

| Setting | Effect |
|---|---|
| `onEntrypointFailure` | `error` (default): an exception in a Fabric initializer stops on NeoForge's error screen. `disable`: the mod is marked failed, its other entrypoints are skipped, and loading continues. Content it registered before failing stays registered; content it built but never registered is dropped, with an error naming it, so it can't stop the registries' freeze. |
| `strictDependencies` | `true`: a Fabric mod with unmet dependencies stops the game on the error screen instead of being skipped. |
| `strictLinkage` | `true`: also disable mods (not only Fabric API modules) that reference Minecraft code missing at runtime. |
| `cache.*` | Translation cache in `.fabricated/cache` in the game directory. Entries are hash-checked before use and deleted after `maxUnusedLaunches` launches without use. |
| `limits.*` | Safety caps applied to every jar (size, entry count, compression ratio, nesting). |
| `mods.<id>.disabled` | Don't load this Fabric mod. |
| `mods.<id>.skipMixins` | Drop specific mixins (`"config.json:Name"` or `"Name"`), or one member of a mixin (`"config.json:Name#member"`: a handler method, or a `@Shadow` field) so its other injections still apply. |
| `mods.<id>.skipEntrypoints` | Don't run these entrypoint types (`main`, `client`, `server`, `preLaunch`, or custom ones). |
| `mods.<id>.aliases` | Extra ids this mod satisfies as a dependency. |
| `mods.<id>.ignorePreflight` | Load the mod even if mixin pre-flight fails. It may crash. Use for testing only. |
| `mods.<id>.ignoreHints` | Ignore the mod's own `custom.fabricated` hints (see [docs/API.md](docs/API.md)). |
| `mods.<id>.loadUnsupported` | Load a mod whose Fabric build Fabricated refuses (the Fabric builds of Sodium and Iris) anyway. Expect it to misbehave; this exists for testing support for it. |
| `mojangMappingsPath` | 1.21.1 and 1.21.11 only: path to Mojang's mapping file if NeoForge's installer didn't leave one. |

## Known limitations

- **Fabric API coverage** is the main limit. See [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) for the
  per-module status on each Minecraft version. What is planned is in [docs/roadmap.md](docs/roadmap.md).
- **Mixin pre-flight is conservative.** It only reports what it can prove from bytecode. A mixin it can't
  evaluate (wildcard selectors, unusual injection points, a target in another Fabric mod) loads normally. If
  that mixin later fails, Mixin's own crash report names the Fabric mod. Mixins into Minecraft, Fabric API and
  installed NeoForge mods (a Fabric addon of a mod's NeoForge build) are all checked.
- **No isolation after mod code runs.** Once a mod's code has run, Java can't unload it. Fabricated then
  explains failures instead of hiding them.
- **Fields NeoForge requires to stay private** (for example, `Biome.climateSettings`) are never widened,
  even if a Fabric access widener asks. Code that reads them directly fails when it runs, and the report
  lists every such case.
- **JPMS packages.** NeoForge requires every Java package to belong to exactly one mod. Fabric allows
  sharing, so clashing mods are de-duplicated (identical files) or one is disabled.

## For mod developers

NeoForge mods can see which Fabric mods are loaded and run code once their initializers have run. Fabric mods can
detect Fabricated with `FabricLoader.getInstance().isModLoaded("fabricated")`. See [docs/API.md](docs/API.md).

## Building

See [CONTRIBUTING.md](CONTRIBUTING.md). In short:

```bash
./gradlew -Pfabricated.coreOnly=true build          # unit tests, no Minecraft needed
./gradlew :mc:neoforge-26.2:fabricatedJar :mc:neoforge-26.2:testmods   # build/libs/fabricated-0.5.0+mc26.2.jar
```

## Design and research

- [docs/roadmap.md](docs/roadmap.md): the roadmap (the platforms' parity, the tracks, what is next), the open findings, test matrices and what is still unverified.
- [docs/FINDINGS.md](docs/FINDINGS.md): every fixed finding, with its cause and how its fix was verified.
- [docs/CHANGELOG.md](docs/CHANGELOG.md): what each working session changed, and how it was verified.
- [docs/MIXINS.md](docs/MIXINS.md): every mixin Fabricated applies, with its target and injection point.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): how the pipeline, isolation, caching and security work.
- [docs/RESEARCH.md](docs/RESEARCH.md): verified facts about NeoForge, FML and Fabric that the design
  relies on.
- [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md): what works, per version and per Fabric API module.

## License

GPL-3.0, see [LICENSE](LICENSE). Third-party material (the Fabric Loader API and vendored Fabric API modules
under Apache-2.0, which GPL-3.0 can include, and Intermediary mappings under CC0) is listed in [NOTICE](NOTICE).
