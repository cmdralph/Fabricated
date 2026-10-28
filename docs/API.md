# Developer API

Fabricated has a small public API for mods that want to work with the Fabric mods it loads. This page covers:

- NeoForge mods that check which Fabric mods are present and wait for their initializers;
- Fabric mods that want to detect Fabricated;
- how to depend on the API.

The API is part of Fabricated and licensed under GPL-3.0, like the rest of the project. The API has no dependencies: no Minecraft, NeoForge or Fabric types. The same jar works on every Minecraft version Fabricated supports. The API version, currently **1.1.0**, follows semantic versioning on its own, separate from Fabricated's release version.

## Depending on it

The API is bundled in every Fabricated jar. Compile against it, but never ship it inside your mod: a NeoForge mod would get a split package, and a copy inside a Fabric mod is removed in favour of Fabricated's own.

```groovy
dependencies {
    // Not published to a maven yet: build it with ./gradlew :api:jar (api/build/libs), or compile against a
    // Fabricated jar, which bundles it.
    compileOnly "io.github.cmdralph.fabricated:fabricated-api:1.1.0"
}
```

**NeoForge mods.** Declare Fabricated as an optional dependency in `neoforge.mods.toml`:

```toml
[[dependencies.yourmod]]
modId = "fabricated"
type = "optional"
versionRange = "*"
ordering = "NONE"
side = "BOTH"
```

Then keep every API call in a class that is only loaded when Fabricated is present:

```java
if (ModList.get().isLoaded("fabricated")) FabricatedCompat.init();
```

```java
final class FabricatedCompat {
    static void init() {
        Fabricated.get().ifPresent(api -> {
            if (api.isFabricModLoaded("some-fabric-mod")) {
                // Talk to it once its initializers have run (registries are still open then).
                api.afterEntrypoints(() -> SomeIntegration.register());
            }
        });
    }
}
```

**Fabric mods.** You rarely need the Java API. To detect Fabricated, use Fabric's own API, which also works on real Fabric Loader:

```java
boolean onNeoForge = FabricLoader.getInstance().isModLoaded("fabricated");
```

To refuse to load with Fabricated versions known not to work, use Fabric's standard `breaks` in `fabric.mod.json`. Fabric Loader ignores the entry when Fabricated is absent.

```json
"breaks": { "fabricated": "<0.2.0" }
```

## Reference (`io.github.cmdralph.fabricated.api`)

`Fabricated.get()` returns `Optional<FabricatedApi>`. It is empty when Fabricated did not set up Fabric mod support this launch: it is not installed, it failed to start, or the installed build is for another Minecraft version.

| `FabricatedApi` method | Returns |
|---|---|
| `fabricatedVersion()`, `apiVersion()`, `fabricApiVersion()` | Fabricated's version, the API version it implements, and the Fabric API release its module plan targets |
| `fabricMods()`, `fabricMod(id)`, `isFabricModLoaded(id)` | Every Fabric mod Fabricated considered, as `FabricMod` records. Look-ups take the Fabric id (`cloth-config`) or the NeoForge id (`cloth_config`). |
| `fabricApiModules()`, `fabricApiModule(id)` | Each Fabric API module, as `FabricApiModule` records, with how it is provided on this version |
| `entrypointsComplete()`, `afterEntrypoints(action)` | Whether the Fabric mods' `main` and `client`/`server` initializers have run. `afterEntrypoints` runs the action once they have (immediately if they already did). |
| `renderReport()` | The current text of `logs/fabric-bridge-report.txt` |

`FabricMod.Status` values:
- `LOADED`
- `FAILED`: loaded, but an initializer threw
- `DISABLED`: could not load (missing dependency, mixin that can't apply, and so on)
- `SKIPPED`: disabled in the config, other side, or replaced by a NeoForge build

`FabricApiModule.Status` values:
- `NATIVE`: Fabricated implements it
- `LOADED`: from the player's Fabric API jar
- `DISABLED`: in the jar, but can't run on NeoForge yet
- `NOT_INSTALLED`
- `UNSUPPORTED`
- `PROVIDED_BY_MOD` (API 1.1.0): another mod provides it (Forgified Fabric API, a module Sodium bundles), and its copy
  is the one loaded; `reason()` names that mod. Fabricated's own copy of a native module stands down for it
  ([`COMPATIBILITY.md`](COMPATIBILITY.md#mods-that-bring-fabric-api-or-fabric-loader)).

`isAvailable()` is true for `NATIVE`, `LOADED` and `PROVIDED_BY_MOD`.

Stability rules:
- Methods may be added to `FabricatedApi` in minor versions, so don't implement it yourself.
- Enum constants may be added, so keep a `default` branch in any `switch` over them.
- The `internal` package is not API.

All methods are thread-safe.

## Hints in `fabric.mod.json` (for Fabric mod authors)

A Fabric mod can tell Fabricated how to run it on NeoForge, without a NeoForge port. Add a `fabricated` block under
`custom`. Fabric Loader ignores it.

```json
"custom": {
  "fabricated": {
    "schema": 1,
    "skipMixins": ["examplemod.mixins.json:client.TooltipMixin"],
    "mixins": ["examplemod.mixins.json", "examplemod.neoforge.mixins.json"],
    "entrypoints": { "main": ["com.example.neoforge.NeoForgeCompat"] },
    "skipEntrypoints": ["modmenu"]
  }
}
```

| Key | Effect under Fabricated |
|---|---|
| `skipMixins` | Leave these mixins out, written `"config.json:Name"` or `"Name"`. Use it for mixins whose target NeoForge changed and whose feature NeoForge already provides. `"config.json:Name#member"` leaves out only the methods or fields of that name (a handler, or a `@Shadow` of a field NeoForge replaced), keeping the mixin's other injections. |
| `mixins` | Use these mixin configs instead of the top-level `mixins` list (same format). The configs must be in the jar. A config owns its whole package, so give a NeoForge-specific config its own package. |
| `entrypoints` | Entrypoints added after the mod's own, same format as Fabric's `entrypoints`. Use them for a NeoForge-specific initializer. They run with the other entrypoints, never during translation. |
| `skipEntrypoints` | Keys of the mod's own entrypoints to leave out. Combine with `entrypoints` to replace one. |

The rules:

- The block is data only. It is checked when the mod's metadata is read and can only change how this mod loads.
- A key with an invalid value is ignored, with a warning in the report. The rest of the block still applies.
- A block with a newer `schema` than Fabricated supports is ignored as a whole.
- Players can turn a mod's hints off with `"ignoreHints": true` for that mod in `config/fabricated.json`.
- Settings that are the player's choice (`ignorePreflight`, `disabled`, `aliases`) are not accepted in the block.

## Compat patches (for modpack makers and third parties)

A compat patch is an ordinary Fabric mod jar that fixes another mod under Fabricated. Its `custom.fabricated` block
lists `patches`, each naming a `target` mod and what to leave out of it:

```json
{
  "schemaVersion": 1, "id": "examplemod-neoforge-fix", "version": "1.0.0",
  "depends": { "examplemod": ">=2.1.0 <2.3.0" },
  "mixins": ["examplemod_fix.mixins.json"],
  "custom": { "fabricated": { "patches": [
    { "target": "examplemod", "skipMixins": ["examplemod.mixins.json:client.TooltipMixin"], "skipEntrypoints": ["modmenu"] }
  ]}}
}
```

How patches behave:

- **They only subtract.** A patch skips the target's mixins and entrypoints. Anything it adds comes from its own mixins
  and entrypoints, which go through the same checks as any other mod.
- **They need a dependency on the target.** A patch applies only while the patch mod `depends` on its target and both
  load. The dependency's version range says which versions of the target the patch fixes; outside it, the patch mod is
  disabled with a clear message.
- **They combine.** Several patches for one mod add up.
- **They are visible.** Applied patches are listed under "Compatibility patches" in the report.
- **Players stay in control.** `"ignoreHints": true` for the target in `config/fabricated.json` turns off the target's
  own hints and every patch for it.

## Planned

- **NeoForge id mappings:** in the hints, mapping a Fabric dependency id to the id of the NeoForge mod that provides
  it.
