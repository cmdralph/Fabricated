# Developer API

Fabricated has a small public API for mods that want to work with the Fabric mods it loads. This page covers:

- NeoForge mods that check which Fabric mods are present and wait for their initializers;
- Fabric mods that want to detect Fabricated;
- how to depend on the API.

The API has no dependencies: no Minecraft, NeoForge or Fabric types. The same jar works on every Minecraft version Fabricated supports. The API version, currently **1.0.0**, follows semantic versioning on its own, separate from Fabricated's release version.

## Depending on it

The API is bundled in every Fabricated jar. Compile against it, but never ship it inside your mod: a NeoForge mod would get a split package, and a copy inside a Fabric mod is removed in favour of Fabricated's own.

```groovy
dependencies {
    // Until the artifact is published, use the fabricated-api jar attached to each GitHub release.
    compileOnly "io.github.cmdralph.fabricated:fabricated-api:1.0.0"
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

Stability rules:
- Methods may be added to `FabricatedApi` in minor versions, so don't implement it yourself.
- Enum constants may be added, so keep a `default` branch in any `switch` over them.
- The `internal` package is not API.

All methods are thread-safe.

## Planned

- **Hints in `fabric.mod.json`:** a `custom.fabricated` block where a Fabric mod's author can skip mixins that can't apply on NeoForge, swap in a NeoForge-specific mixin config or initializer, and map dependency ids to NeoForge mod ids. It is data only, read when the mod's metadata is read.
- **Compat patches:** small Fabric-format jars that fix a specific mod under Fabricated. They can skip that mod's mixins and initializers, and add their own.
