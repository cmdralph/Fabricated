# Compatibility

Everything in this file comes from CI runs on a real NeoForge dedicated server (`ci/smoke-server.sh`), not
from expectations. CI boots each version twice:

1. **Test mods only**: Fabricated plus the Fabric test mods in `mc/common/src/testmod*`. This is a pass/fail
   gate.
2. **With the official Fabric API jar** added: the server must start (a gate); the other checks are
   informational. The resulting `fabric-bridge-report.txt` is kept as a CI artifact
   (`smoke-fabric-api-<version>`), and the tables below are taken from it.

Last updated from CI run [38171595789](https://github.com/cmdralph/Fabricated/actions/runs/38171595789)
(Fabricated 0.1.0 development build, dedicated server).

## Minecraft versions

| Minecraft | NeoForge | Fabric API tested | Test mods | Server boots with Fabric API |
|---|---|---|---|---|
| 1.21.11 | 21.11.45 | 0.141.6+1.21.11 | ✅ all 13 checks | ✅ boots, all test-mod checks still pass |
| 26.1.2 | 26.1.2.116 | 0.155.3+26.1.2 | ✅ all 13 checks | ✅ boots, all test-mod checks still pass |
| 26.2 | 26.2.0.90 | 0.161.0+26.2 | ✅ all 13 checks | ✅ boots, all test-mod checks still pass |
| 26.3 (beta) | 26.3.0.69-beta | 0.162.0+26.3 | ✅ all 13 checks | ✅ boots, all test-mod checks still pass |

"Boots with Fabric API" means the server starts with the full official Fabric API jar installed. Modules
that can't work on NeoForge are disabled before any code runs (see below), and the rest load alongside
Fabricated's test mods.

The 13 test-mod checks: the server starts; a `main` entrypoint runs; the Fabric Loader API works; a custom
entrypoint resolves; content registers directly into vanilla registries; `fabric-api-base` events fire; an
access widener applies; a `server` entrypoint runs; a mixin applies; a mod with a missing dependency is
reported, naming the dependency; a mod with an impossible mixin is caught by pre-flight; and a mod whose
entrypoint throws is isolated while the server keeps running.

## Fabric API modules

How to read the table:

- **native**: Fabricated provides the module itself.
- **loads**: the module from the official jar passes mixin pre-flight and loads.
- **disabled**: the module can't load on NeoForge yet (reason below), and mods that require it are
  disabled with a message.
- **client**: client-only module, so not loaded on a dedicated server (the client isn't tested in CI yet).
- **unsupported**: deliberately not loaded.
- **—**: the module doesn't exist in that Fabric API version.

| Module | 1.21.11 | 26.1.2 | 26.2 | 26.3 |
|---|---|---|---|---|
| fabric-api-base | native | native | native | native |
| fabric-crash-report-info-v1 | native | native | native | native |
| fabric-biome-api-v1 | loads | loads | loads | loads |
| fabric-block-getter-api-v2 / block-view-api-v2 | loads | loads | loads | loads |
| fabric-message-api-v1 | loads | loads | loads | loads |
| fabric-rendering-fluids-v1 | loads | loads | loads | loads |
| fabric-serialization-api-v1 | loads | loads | loads | loads |
| fabric-transitive-access-wideners-v1 | loads | loads | loads | loads |
| fabric-game-rule-api-v1 | disabled | loads | loads | loads |
| fabric-permission-api-v1 | — | loads | loads | loads |
| fabric-lifecycle-events-v1 | native | native | native | native |
| fabric-networking-api-v1 | native | native | native | native |
| fabric-registry-sync-v0 | disabled | disabled | disabled | disabled |
| fabric-resource-loader-v1 | native | native | native | native |
| fabric-resource-loader-v0 | loads | loads | loads | loads |
| fabric-item-api-v1 | disabled | disabled | disabled | disabled |
| fabric-block-api-v1 | disabled | disabled | disabled | disabled |
| fabric-transfer-api-v1 | disabled | disabled | disabled | disabled |
| fabric-content-registries-v0 | disabled | disabled | disabled | disabled |
| fabric-object-builder-api-v1 | disabled | disabled | disabled | disabled |
| fabric-entity-events-v1 | disabled | disabled | disabled | disabled |
| fabric-events-interaction-v0 | disabled | disabled | disabled | disabled |
| fabric-dimensions-v1 | disabled | disabled | disabled | disabled |
| fabric-loot-api-v3 (and v2) | disabled | disabled | disabled | disabled |
| fabric-resource-conditions-api-v1 | disabled | disabled | disabled | disabled |
| fabric-tag-api-v1 | disabled | disabled | disabled | disabled |
| fabric-advancement-api-v1 | — | — | loads | disabled |
| fabric-menu-api-v1 / screen-handler-api-v1 | disabled | disabled | disabled | disabled |
| fabric-api-lookup-api-v1 | loads | loads | loads | loads |
| fabric-command-api-v2 | loads | loads | loads | loads |
| fabric-convention-tags-v2 (and v1) | loads | loads | loads | loads |
| fabric-data-attachment-api-v1 | disabled | disabled | disabled | disabled |
| fabric-particles-v1 | disabled | disabled | disabled | disabled |
| fabric-recipe-api-v1 | disabled | disabled | disabled | disabled |
| fabric-creative-tab-api-v1 / item-group-api-v1 | loads | loads | loads | loads |
| fabric-debug-api-v1 | — | disabled | disabled | disabled |
| fabric-key-mapping-api-v1 / key-binding-api-v1, screen-api-v1, rendering-v1, sound-api-v1, model-loading-api-v1 | client | client | client | client |
| fabric-renderer-api-v1, fabric-renderer-indigo | unsupported | unsupported | unsupported | unsupported |
| fabric-data-generation-api-v1, gametest, client-gametest | unsupported | unsupported | unsupported | unsupported |

### Why modules are disabled

Pre-flight reads NeoForge's patched Minecraft bytecode and checks each Fabric API mixin against it. The
failures fall into a few groups:

- **NeoForge changed the code the mixin targets.** This is the most common cause. Examples from the
  reports:
  - `fabric-block-api-v1`: NeoForge replaces `BlockState.isAir()` calls in chunk sections.
  - `fabric-registry-sync-v0`: `Bootstrap.bootStrap` no longer calls `wrapStreams()`.
  - `fabric-networking-api-v1`: NeoForge replaces the custom-payload codec setup in
    `ClientboundCustomPayloadPacket`/`ServerboundCustomPayloadPacket`, because NeoForge has its own payload
    system.
- **NeoForge recompiles Minecraft, so names of synthetic members differ** (1.21.11): lambda methods such as
  `lambda$loadResources$1` and captured fields such as `val$tags` have different names in NeoForge's build.
- **The mixin targets a library class**: `fabric-dimensions-v1` patches DataFixerUpper's `TaggedChoice`,
  which NeoForge loads without mixin support.
- **NeoForge's build gives a lambda different parameters**: an `@Inject` handler must take the target
  method's parameters in order. `fabric-loot-api-v3` (26.1.2, 26.2) and `fabric-resource-conditions-api-v1`
  inject into synthetic lambdas whose captured values NeoForge's build passes in another order or not at all.
- **A mixin adds a method NeoForge already added**: `fabric-recipe-api-v1` generates an accessor
  `getConnection()` on `ServerCommonPacketListenerImpl` (NeoForge adds a public one), and on 26.1.2 its
  `IngredientMixin` merges a `hashCode()` that NeoForge's `Ingredient` already has, and `fabric-tag-api-v1`
  merges a `remove()` into `TagFile`, which NeoForge already declares. Mixin rejects all three.
- **NeoForge calls a different constructor**: `fabric-particles-v1` modifies `new BlockParticleOption(type,
  state)` in `Entity.spawnSprintParticle`; NeoForge passes the block position as a third argument.
- **Dependency cascade**: about a third of the disabled modules only fail because they require one of the
  above. For example, `fabric-object-builder-api-v1` and `fabric-debug-api-v1` need registry sync, and
  `fabric-data-attachment-api-v1` needs entity-events.

Each of these crashed the server at some point during development: they are the reason the pre-flight checks
exist. Now each is a clear line in the report, and the server starts with the complete Fabric API jar on all
four versions.

### Native `fabric-lifecycle-events-v1`

Upstream's mixins don't apply on NeoForge (for example, NeoForge moved the `blockEntities.remove` call in
`LevelChunk.getBlockEntity`). Fabricated therefore ships the module's unchanged public API and fires its
events from NeoForge's events. CI checks `SERVER_STARTED`, `END_SERVER_TICK` and `SERVER_STOPPING` on a
real server for every version.

| Fabric events | Fired from (NeoForge) | Notes |
|---|---|---|
| Server starting/started/stopping/stopped | `ServerStartingEvent` … `ServerStoppedEvent` | |
| Server and level ticks (start/end) | `ServerTickEvent`, `LevelTickEvent` | |
| Level load/unload | `LevelEvent.Load/Unload` | |
| Chunk load/generate/unload | `ChunkEvent.Load/Unload` | `CHUNK_GENERATE` fires for newly generated chunks |
| Full chunk status / chunk level type change | `ChunkTicketLevelUpdatedEvent` | fired when a loaded chunk's ticket level changes |
| Entity load/unload, `ALLOW_LOAD` (26.x) | `EntityJoinLevelEvent`, `EntityLeaveLevelEvent` | `ALLOW_LOAD` cancels the join |
| `EntityLoadData` (26.x) | `EntityJoinLevelEvent`, `FinalizeSpawnEvent` | spawn reason is known for mobs and null for other entities, which the API allows |
| Equipment change | `LivingEquipmentChangeEvent` | |
| Block entity load/unload | `ChunkEvent.Load/Unload` | only for block entities of chunks that load or unload, not ones placed or removed in a loaded chunk |
| Sync data pack contents | `OnDatapackSyncEvent` | |
| Tags loaded | `TagsUpdatedEvent` | |
| Client: ticks, chunks, entities, block entities | `ClientTickEvent`, `LevelTickEvent`, `ChunkEvent`, `EntityJoin/LeaveLevelEvent` | client isn't tested in CI yet |
| Client started / stopping | first client tick / `GameShuttingDownEvent` | |
| After client level change | `ClientPlayerNetworkEvent.LoggingIn` and `.Clone` | fires on login and on respawn into another level |
| **Not fired**: start/end data pack reload, before/after save | none | NeoForge has no matching hook |

### Native `fabric-networking-api-v1`

Fabric payload types are registered with NeoForge as optional payloads when NeoForge locks its network registry
(after every mod initialiser), so they travel through NeoForge's own networking, including its large-payload
splitting. Play handlers run on the main thread and configuration handlers on the network thread, as in Fabric.

| Fabric | NeoForge |
|---|---|
| `PayloadTypeRegistry` (play and configuration, both directions) | `RegisterPayloadHandlersEvent` registrar (`optional()`) |
| Global and per-connection receivers, `PacketContext` (26.x API) | payload handlers, dispatched by connection |
| `canSend` / `getSendable` | NeoForge's channel negotiation (`hasChannel`) |
| `ServerPlayConnectionEvents`, channel `REGISTER` events | `PlayerLoggedIn/OutEvent` |
| `ServerConfigurationConnectionEvents.BEFORE_CONFIGURE/CONFIGURE`, `addTask`, `completeTask` | `RegisterConfigurationTasksEvent`, `finishCurrentTask` |
| `EntityTrackingEvents` | `PlayerEvent.StartTracking/StopTracking` |
| `ServerPlayNetworking.reconfigure` | vanilla `switchToConfig()` |
| Client play/configuration connection events | `ClientPlayerNetworkEvent`, configuration listener creation |

Not supported: **login queries** (`ServerLoginNetworking`, `ClientLoginNetworking`): NeoForge exposes no
login-phase hooks. The API links, so mods load, but login receivers are never called; mods should use the
configuration phase instead (Fabric's own recommendation since 1.20.2). Configuration-phase
`DISCONNECT` events do not fire. A payload id registered with different codecs per direction uses the
serverbound codec for both (NeoForge keeps one codec per id); the report says so when it happens.
CI checks that a Fabric payload and receiver register and reach NeoForge; a client round trip is not yet
tested in CI.

### Native `fabric-resource-loader-v1`

Upstream patches the reload-listener and pack-repository code that NeoForge has already replaced with its own
sorted reload-listener events. Fabricated ships the module's unchanged public API on top of those events:

| Fabric | NeoForge |
|---|---|
| Server data listeners, listener factories (`DataResourceLoader`) | `AddServerReloadListenersEvent` |
| Client resource listeners | `AddClientReloadListenersEvent` |
| `addListenerOrdering` / `addReloaderOrdering` | `addDependency`; Fabric's vanilla ids (`minecraft:recipes`, `minecraft:languages`, …) are translated to NeoForge's (`minecraft:recipe_manager`, `minecraft:language_manager`, …) |
| `fabric:before_vanilla`, `fabric:after_vanilla` phases | empty listeners before the first and after the last vanilla listener |
| Shared reload state: registries, feature flags, data store (and on 1.21.11, 26.1.2 and 26.2 the recipe and advancement managers) | a setup listener that runs before all others |
| `MinecraftServer` as `DataResourceStore`, `Resource#getFabricPackSource` | interfaces added by mixins that only add methods |
| `registerBuiltinPack` | `AddPackFindersEvent` |

Mod resources themselves need nothing: NeoForge already loads the `assets/` and `data/` of every mod, Fabric mods
included. Resources from those mod packs report Fabric's mod pack source, as on Fabric.

Differences from upstream:

- An ordering that names a listener nobody registered is skipped (logged at debug level). An ordering that would
  create a cycle is skipped with a warning in the report, where NeoForge would otherwise stop the game.
- `DEFAULT_ENABLED` built-in **data** packs are enabled in new worlds. `DEFAULT_ENABLED` built-in **resource**
  packs are not switched on automatically (upstream patches the options for this); players enable them in the
  pack screen. `ALWAYS_ENABLED` packs are required on both sides.
- Fabric's `/datapack` protections for its internal packs don't apply; NeoForge's own rules do.

CI checks, on all four versions: listener ordering (including an ordering against a vanilla listener), the
shared reload state, a listener factory, the data store read back through `MinecraftServer`, a `DEFAULT_ENABLED`
built-in data pack being enabled and loaded, and the listener running again after `/reload`. Client listeners
compile against NeoForge's client event but aren't run in CI yet.

### What this means for Fabric mods

A Fabric mod works today if it needs only the Fabric Loader API and the **native** or **loads** modules
above. Most content mods also need registry sync, so they are disabled with a message naming the missing module.

### Planned work, in order of impact

These modules are to be implemented natively on NeoForge's own events and APIs. Each one also unblocks the
modules that cascade from it.

1. ✅ `fabric-lifecycle-events-v1` (done; unblocked `fabric-api-lookup-api-v1` and, with a linkage-check fix
   for injected interfaces, `fabric-convention-tags-v2`/`v1`).
2. ✅ `fabric-networking-api-v1` on 26.x (done). Its dependants now load or fail on their own mixins
   (particles, recipe-api). Ported to 1.21.11 as well.
3. ✅ `fabric-resource-loader-v1` on all four versions (done). Unblocked the v0 API, creative-tab /
   item-group and, on 26.2, advancement-api. tag-api, loot-api and resource-conditions now fail on their own
   mixins (see above).
4. `fabric-registry-sync-v0`: NeoForge already syncs registries, so this module only needs to provide its
   API. Unblocks object-builder.
5. `fabric-entity-events-v1`: NeoForge's entity events. Unblocks data-attachment.

## Fabric mods tested

Only Fabricated's own test mods are tested in CI so far. Results for real Fabric mods will be added here as
CI covers them. To report one, see [CONTRIBUTING.md](../CONTRIBUTING.md#reporting-a-compatibility-result).
