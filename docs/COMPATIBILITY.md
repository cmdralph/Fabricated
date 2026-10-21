# Compatibility

Everything in this file comes from CI runs on a real NeoForge dedicated server (`ci/smoke-server.sh`) and a
real NeoForge client (`ci/smoke-client.sh`), not from expectations. CI starts each version three times:

1. **Test mods only**: Fabricated plus the Fabric test mods in `mc/common/src/testmod*`. This is a pass/fail
   gate.
2. **With the official Fabric API jar** added: the server must start (a gate); the other checks are
   informational. The resulting `fabric-bridge-report.txt` is kept as a CI artifact
   (`smoke-fabric-api-<version>`), and the tables below are taken from it.
3. **Client**: a real NeoForge client, started headlessly, joins a singleplayer world with Fabricated and the test
   mod (client entrypoint, client lifecycle, a client resource listener, a networking round trip with the
   integrated server). This is a pass/fail gate.

Last updated from CI run [38491176425](https://github.com/cmdralph/Fabricated/actions/runs/38491176425)
(Fabricated 0.1.0 development build).

## Minecraft versions

| Minecraft | NeoForge | Fabric API tested | Test mods | Server boots with Fabric API |
|---|---|---|---|---|
| 1.21.11 | 21.11.45 | 0.141.6+1.21.11 | ✅ every server and client check | ✅ boots, all test-mod checks still pass |
| 26.1.2 | 26.1.2.116 | 0.155.3+26.1.2 | ✅ every server and client check | ✅ boots, all test-mod checks still pass |
| 26.2 | 26.2.0.90 | 0.161.0+26.2 | ✅ every server and client check | ✅ boots, all test-mod checks still pass |
| 26.3 (beta) | 26.3.0.69-beta | 0.164.0+26.3 | ✅ every server and client check | ✅ boots, all test-mod checks still pass |

"Boots with Fabric API" means the server starts with the full official Fabric API jar installed. Modules
that can't work on NeoForge are disabled before any code runs (see below); one that can do without a mixin NeoForge's
code can't take loads without it (LEAVE-1); the rest load alongside Fabricated's test mods.

The server checks: the server starts; a `main` entrypoint runs; the Fabric Loader API works; a custom
entrypoint resolves; content registers directly into vanilla registries; `fabric-api-base` events fire; an
access widener applies; a `server` entrypoint runs; a mixin applies; a mod with a missing dependency is
reported, naming the dependency; a mod with an impossible mixin is caught by pre-flight and loads without it; a mod whose
entrypoint throws is isolated while the server keeps running; Fabricated's public API answers and its
after-initializers callback runs; a client-only interface listed in `@EnvironmentInterface` is stripped on the
server; and no test mod check reports a failure. The others cover the native Fabric API modules (described in
their sections below) and fixes the roadmap lists, each named in `ci/smoke-server.sh` by its finding. The client checks: the client starts and joins a singleplayer world, the `main` and
`client` entrypoints run, the client-side native modules work, including a networking round trip and a
configuration task with the integrated server, an extended menu opens with its data, a Mod Menu config screen
factory is registered with NeoForge, and no test mod check reports a failure.

CI also starts the client with the full Fabric API jar, for information only (it doesn't fail the build). It
joins its world and passes every client check on all four versions. On 26.x every Fabric API module loads on the
client, the renderer API, Indigo and model loading included, and on 1.21.11 too since this pass. That run checks on
1.21.11 that a model file of a Fabric type is read by its Fabric deserializer and that Fabric block models are drawn in
chunk sections. On the dedicated server no Fabric API module is disabled.

The 26.3 job is marked experimental in CI because NeoForge 26.3 is a beta; it passes every check. Its client
opens its window with SDL3 over EGL, so CI installs Mesa's EGL library for it.

## Fabric API modules

How to read the table:

- **native**: Fabricated provides the module itself.
- **loads**: the module from the official jar passes mixin pre-flight and loads.
- **disabled**: the module can't load on NeoForge yet (reason below), and mods that require it are
  disabled with a message.
- **client**: client-only module, so not loaded on a dedicated server; its client status is in the rows below.
- **unsupported**: deliberately not loaded.
- **—**: the module doesn't exist in that Fabric API version.

| Module | 1.21.11 | 26.1.2 | 26.2 | 26.3 |
|---|---|---|---|---|
| fabric-api-base | native | native | native | native |
| fabric-crash-report-info-v1 | native | native | native | native |
| fabric-biome-api-v1 | loads | loads | loads | native (see below) |
| fabric-block-getter-api-v2 / block-view-api-v2 | loads | loads | loads | loads |
| fabric-message-api-v1 | loads | loads | loads | loads |
| fabric-rendering-fluids-v1 | loads | loads | loads | loads |
| fabric-serialization-api-v1 | loads | loads | loads | loads |
| fabric-transitive-access-wideners-v1 | loads | loads | loads | loads |
| fabric-game-rule-api-v1 | loads | loads | loads | loads |
| fabric-permission-api-v1 | — | loads | loads | loads |
| fabric-lifecycle-events-v1 | native | native | native | native |
| fabric-networking-api-v1 | native | native | native | native |
| fabric-registry-sync-v0 | native | native | native | native |
| fabric-resource-loader-v1 | native | native | native | native |
| fabric-resource-loader-v0 | loads | loads | loads | loads |
| fabric-item-api-v1 | native | native | native | native |
| fabric-block-api-v1 | native | native | native | native |
| fabric-transfer-api-v1 | native | native | native | native |
| fabric-content-registries-v0 | native | native | native | native |
| fabric-object-builder-api-v1 | native | native | native | native |
| fabric-entity-events-v1 | native | native | native | native |
| fabric-events-interaction-v0 | native | native | native | native |
| fabric-dimensions-v1 | loads | loads | loads | loads |
| fabric-loot-api-v3 | native | native | native | native |
| fabric-resource-conditions-api-v1 | loads | loads | loads | loads |
| fabric-tag-api-v1 | loads | loads | loads | loads |
| fabric-advancement-api-v1 | — | — | loads | loads |
| fabric-menu-api-v1 / screen-handler-api-v1 | native | native | native | native |
| fabric-api-lookup-api-v1 | native | native | native | native |
| fabric-command-api-v2 | loads | loads | loads | loads |
| fabric-convention-tags-v2 (and v1) | loads | loads | loads | loads |
| fabric-data-attachment-api-v1 | loads | loads | loads | loads |
| fabric-particles-v1 | loads | loads | loads | loads |
| fabric-recipe-api-v1 | loads | loads | loads | loads |
| fabric-creative-tab-api-v1 / item-group-api-v1 | loads | loads | loads | loads |
| fabric-debug-api-v1 | — | loads | loads | loads |
| fabric-key-mapping-api-v1 / key-binding-api-v1, screen-api-v1, rendering-v1, sound-api-v1, model-loading-api-v1 | client | client | client | client |
| fabric-rendering-v1 on the client | loads (see below) | loads (see below) | loads (see below) | loads (see below) |
| fabric-screen-api-v1, sound-api-v1 on the client | loads (see below) | loads (see below) | loads (see below) | loads (see below) |
| fabric-model-loading-api-v1 on the client | loads without renderer-api (see below) | loads (see below) | loads (see below) | loads (see below) |
| fabric-renderer-api-v1, fabric-renderer-indigo | unsupported | client (see below) | client (see below) | client (see below) |
| fabric-data-generation-api-v1 (its classes; it generates nothing unless `fabric-api.datagen` is set; its tag appender mixins, and on 26.3 its loot sub-provider mixins, are left out) | loads | loads | loads | loads |
| fabric-gametest-api-v1, client-gametest | unsupported | unsupported | unsupported | unsupported |

### Why modules are disabled

Pre-flight reads NeoForge's patched Minecraft bytecode and checks each Fabric API mixin against it. The
failures fall into a few groups:

- **NeoForge changed the code the mixin targets.** This is the most common cause. Examples from the
  reports:
  - `fabric-block-api-v1` (native now): NeoForge replaces `BlockState.isAir()` calls in chunk sections.
  - `fabric-networking-api-v1`: NeoForge replaces the custom-payload codec setup in
    `ClientboundCustomPayloadPacket`/`ServerboundCustomPayloadPacket`, because NeoForge has its own payload
    system.
- **NeoForge recompiles Minecraft, so names of synthetic members differ** (1.21.11): lambda methods such as
  `lambda$loadResources$1` and captured fields such as `val$tags` have different names in NeoForge's build.
- **The mixin targets a library class**: `fabric-dimensions-v1` patches DataFixerUpper's `TaggedChoice`,
  which NeoForge loads without mixin support. Fabricated's Fabric API plan
  (`fabric-api-plan.json`) leaves out those mixins and the mixin that depends on them, so the rest of the
  module loads; only the safety net for worlds that reference a removed modded dimension is missing.
- **NeoForge changed a field's type**: an `@Accessor` must read a field of exactly its type. Upstream's
  `fabric-object-builder-api-v1` (26.x) read `AttributeSupplier.Builder.builder` as an `ImmutableMap.Builder`,
  which NeoForge replaced; it is native now (see below).
- **NeoForge's build gives a lambda different parameters**: an `@Inject` handler must take the target
  method's parameters in order. `fabric-loot-api-v3` (26.1.2, 26.2; native now) and `fabric-resource-conditions-api-v1`
  inject into synthetic lambdas whose captured values NeoForge's build passes in another order or not at all.
  NeoForge rewrote that code around its own conditions, so Fabricated evaluates `fabric:load_conditions` inside
  NeoForge's conditional codecs instead (with upstream's conditions), and the module loads.
- **A mixin adds a method NeoForge already added**: `fabric-recipe-api-v1` generates an accessor
  `getConnection()` on `ServerCommonPacketListenerImpl` (NeoForge adds a public one), on 26.1.2 and 1.21.11 its
  `IngredientMixin` merges a `hashCode()` that NeoForge's `Ingredient` already has, and `fabric-tag-api-v1`
  merges a `remove()` into `TagFile`, which NeoForge already declares. Mixin rejects all three. Fabricated now
  turns an accessor whose method the target already declares publicly into a shadow of that method (for any
  mod, reported as a warning), and the plans leave out the other two methods: NeoForge's `hashCode` stays, and
  NeoForge's own tag `remove` list, which NeoForge applies, also receives `fabric:remove` entries. recipe-api
  and tag-api load on all versions.
- **NeoForge's build names a captured variable differently**: an anonymous class keeps each local variable it
  captures in a synthetic field named after it (`val$tags`). NeoForge's 1.21.11 build is recompiled from
  decompiled code, so the variable, and with it the field, has another name. `fabric-tag-api-v1` reads and writes
  `val$tags` in `MappedRegistry$TagSet$2` with an accessor. When an `@Accessor` names a missing `val$` field and
  the target has exactly one captured field of the same type, Fabricated points the accessor at that field (for
  any mod, reported as a warning).
- **NeoForge calls a different constructor**: `fabric-particles-v1` modifies `new BlockParticleOption(type,
  state)` in `Entity.spawnSprintParticle`; NeoForge passes the block position as a third argument. The plan
  leaves out that mixin and the entity one next to it (they only make sprint and fall particles block-aware),
  so the module loads. NeoForge also re-keyed the particle provider map the module registers into;
  Fabricated adds vanilla's map back next to NeoForge's, forwarding to it. On 1.21.11 the module also reads a
  networking-internal interface of upstream's networking module, which Fabricated provides as upstream does (the
  module then always sends the particle encoding every client reads).
- **NeoForge calls a different overload**: on 26.3 NeoForge loads the reloadable registries through its own
  `RegistryDataLoader.load` overload (with the updated tags), so `fabric-advancement-api-v1`'s reload mixin finds
  nothing to wrap. Fabricated wraps NeoForge's call instead and runs upstream's reload hooks around it
  (advancement modifications see the registries being loaded; `AdvancementEvents.ALL_LOADED` fires).
- **NeoForge moved a method's code into a new overload**: NeoForge often keeps vanilla's signature as a method that
  calls a new overload of the same name holding the code (`SimpleModelWrapper.bake`, `ModelManager`'s model
  discovery). A selector without a descriptor picks the first method of that name, as Mixin does, so an injection
  point inside the code is missing there. When the points all occur in the one overload that method calls, and the
  handler doesn't depend on the method's parameters (an `@Inject` taking only the callback info, a `@WrapOperation`,
  `@ModifyExpressionValue`, `@Redirect` and so on), Fabricated points the injector at the overload (for any mod,
  reported as a warning).
- **A mixin uses an unsupported module**: Mixin fails on a class it can't find as it applies a mixin, so a mod
  whose mixin uses a class only an unsupported Fabric API module has (Continuity's use the renderer API, which can't
  run on NeoForge 1.21.11) is disabled before launch with the reason. When the mixin's config has a plugin, which may
  skip it at runtime, or isn't required, only that mixin is left out.
- **Dependency cascade**: a module that requires a disabled module is disabled too. In the current CI runs no
  Fabric API module is disabled only for that reason; `fabric-data-attachment-api-v1`, which needed
  object-builder, loads now.

Each of these crashed the server at some point during development: they are the reason the pre-flight checks
exist. Now each is a clear line in the report, and the server starts with the complete Fabric API jar on all
four versions; on 26.x no Fabric API module is disabled any more.

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
| Block entity load/unload | `LevelChunk` (Fabricated mixin, at upstream's points) and `ChunkEvent.Unload` | as on Fabric: when a block entity enters or leaves its chunk (loading with it, placed, broken or replaced later), and for those a chunk holds when it unloads. On the client also when the server resends a chunk, and when the client drops its level (disconnect, dimension change) |
| Sync data pack contents | `OnDatapackSyncEvent` | |
| Tags loaded | `TagsUpdatedEvent` | |
| Client: ticks, chunks, entities, block entities | `ClientTickEvent`, `LevelTickEvent`, `ChunkEvent`, `EntityJoin/LeaveLevelEvent` | chunks the client drops without NeoForge's event (a smaller view distance, a reused slot) unload too, as upstream |
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
configuration phase instead (Fabric's own recommendation since 1.20.2). Configuration-phase `DISCONNECT` events
fire when a connection closes during configuration, from the configuration listener's `onDisconnect` on the server or
client thread (Fabric API may fire them from the network thread instead, as the channel closes). A payload id can use a different codec in each direction, as on Fabric
(NeoForge keeps one codec per id, so Fabricated supplies the clientbound one when NeoForge looks it up).
CI checks that a Fabric payload and receiver register and reach NeoForge on a dedicated server, that a
configuration listener closed there fires `DISCONNECT` once, and, on the client, a play-phase round trip and a
configuration task with the integrated server. The client's `DISCONNECT` has no check: it would end the client's own
connection.

### Native `fabric-resource-loader-v1`

Upstream patches the reload-listener and pack-repository code that NeoForge has already replaced with its own
sorted reload-listener events. Fabricated ships the module's unchanged public API on top of those events:

| Fabric | NeoForge |
|---|---|
| Server data listeners, listener factories (`DataResourceLoader`) | `AddServerReloadListenersEvent` |
| Client resource listeners | `AddClientReloadListenersEvent` |
| A listener registered the vanilla way on the client's resource manager before that event (from a mixin into `Minecraft`'s constructor) | held back and added through the event, after vanilla's: NeoForge's event refuses listeners it can't name |
| `addListenerOrdering` / `addReloaderOrdering` | `addDependency`; Fabric's vanilla ids (`minecraft:recipes`, `minecraft:languages`, …) are translated to NeoForge's (`minecraft:recipe_manager`, `minecraft:language_manager`, …) |
| `fabric:before_vanilla`, `fabric:after_vanilla` phases | empty listeners before the first and after the last vanilla listener |
| Shared reload state: registries, feature flags, data store (and on 1.21.11, 26.1.2 and 26.2 the recipe and advancement managers) | a setup listener that runs before all others |
| `MinecraftServer` as `DataResourceStore`, `Resource#getFabricPackSource` | interfaces added by mixins that only add methods |
| `registerBuiltinPack` | `AddPackFindersEvent` |

A listener registered the vanilla way (`ReloadableResourceManager.registerReloadListener`, as OneConfig does from a
mixin into `Minecraft`'s constructor) after NeoForge has sorted the client's listeners is added last, as vanilla adds
it, and runs from the next reload; NeoForge itself would throw there.

Mod resources themselves need nothing: NeoForge already loads the `assets/` and `data/` of every mod, Fabric mods
included. Resources from those mod packs report Fabric's mod pack source, as on Fabric. What only NeoForge reads in a
Fabric jar built for both loaders is left out, as Fabric ignores it: the `neoforge:` sections of its `pack.mcmeta`,
and the `neoforge:conditions` of its data files, which name its NeoForge build's own condition types (Tectonic's,
DATA-1); the Fabric build's `fabric:load_conditions` still decide. A built-in pack (`resourcepacks/<name>`, Fabric API's
`registerBuiltinPack`) is read with those changes too (PACK-2), and has Fabric API's known-pack identity, so the
registry entries it adds are stable and a world with them opens without an experimental-settings warning (PACK-3).
A mod that adds a pack source the vanilla way, replacing the repository's sources with a new immutable set
(FrozenLib's resource pack API), keeps it: the set is made mutable again before NeoForge adds its own pack finders
(PACK-1).

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
are checked by the client CI run (a client resource listener runs while the client starts).

### Native `fabric-registry-sync-v0`

NeoForge already syncs registry ids to clients and checks that both sides have the same entries, so Fabric's own
sync protocol isn't needed (and isn't sent). Fabricated provides the module's unchanged public API on NeoForge's
registries:

| Fabric | NeoForge |
|---|---|
| `FabricRegistryBuilder` (new static registries) | registered into the root registry while NeoForge's registration events run, when Fabric initializers run; NeoForge freezes, syncs and snapshots them with the rest |
| `RegistryAttribute.SYNCED` | NeoForge's sync flag; registries NeoForge syncs also report `SYNCED` |
| `RegistryEntryAddedCallback` | NeoForge's registry add callback |
| `RegistryIdRemapCallback` | NeoForge's clear and bake callbacks around applying a registry snapshot (for example the server's ids on joining it) |
| `DynamicRegistries.register` / `registerSynced` (and `registerReloadable` on 26.3) | NeoForge's data-pack registry lists |
| `FabricRegistry#addAlias` | NeoForge's registry aliases |

Differences from upstream:

- `DynamicRegistrySetupCallback` fires once the dynamic registries are loaded (on the server before it starts,
  on the client when joining a server), not before they are filled. Entry-added callbacks registered on its view
  are called once for every entry already present. Entries can't be added to the registries from the callback.
- A registry must be created while registration is open, which is when Fabric initializers run. Creating one
  later fails, as it does on NeoForge.
- `SKIP_WHEN_EMPTY` has no effect; NeoForge decides how to sync an empty registry. The `MODDED`, `OPTIONAL` and
  `SAVE_DATA_VALIDATED` attributes are recorded for mods that read them but change nothing.
- NeoForge has no public API for the sync flag and the data-pack registry lists, so Fabricated reaches them by
  reflection. If a NeoForge build lacks them, the affected call fails with a clear message instead of the game
  failing to start.

CI checks, on all four versions: a new synced registry created and filled from a Fabric initializer (with the
entry-added callback and an alias), and a synced data-pack registry loaded from a data pack and seen by a
`DynamicRegistrySetupCallback`. Id remapping on joining a server needs a client and isn't tested in CI yet.

### Native `fabric-entity-events-v1`

Upstream's mixins don't apply on NeoForge, so Fabricated ships the module's unchanged public API and fires its
events from NeoForge's entity events. All of them are server-side, as upstream.

| Fabric events | Fired from (NeoForge) | Notes |
|---|---|---|
| `ServerLivingEntityEvents.ALLOW_DAMAGE` | `LivingIncomingDamageEvent` | returning false cancels the hit |
| `AFTER_DAMAGE` | `LivingDamageEvent.Post` | not fired for the hit that kills, as upstream; "damage taken" is after shields, before armor |
| `ALLOW_DEATH` (living and `ServerPlayerEvents`) | `LivingDeathEvent` (highest priority) | returning false cancels the death; the amount is the killing hit's damage |
| `AFTER_DEATH`, `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY` | `LivingDeathEvent` (lowest priority, not cancelled) | |
| `MOB_CONVERSION` | `LivingConversionEvent.Post` | NeoForge doesn't pass the conversion parameters; vanilla's usual ones are given |
| `ServerPlayerEvents.COPY_FROM`, `AFTER_RESPAWN`, `JOIN`, `LEAVE` | `PlayerEvent.Clone`, `PlayerRespawnEvent`, `PlayerLoggedIn/OutEvent` | |
| Level change (player and other entities) | `PlayerChangedDimensionEvent`; `EntityTravelToDimensionEvent` then the copy joining the destination | |
| `ServerMobEffectEvents` and `FabricMobEffect` hooks | `MobEffectEvent.Applicable`, `Added`, `Remove`, `Expired` | `AFTER_REMOVE` runs as the server's next task, once the effect is gone; `isFromCommand()` is always false |
| `EntitySleepEvents.ALLOW_SLEEPING`, `STOP_SLEEPING` (players), `ALLOW_SETTING_SPAWN` | `CanPlayerSleepEvent`, `PlayerWakeUpEvent`, `PlayerSetSpawnEvent` | |
| **Not fired**: `EntityElytraEvents`, the other sleep events | none | NeoForge has no matching hook |

CI checks, on all four versions: mob effect add and remove, damage, and death events for a mob, in order.

### Native `fabric-object-builder-api-v1`

Upstream's mixins don't fit NeoForge: NeoForge changed the attribute builder's field type, the villager-trade
constructor and the entity data serializer table. Fabricated therefore ships the unchanged public API and
implements it on NeoForge's registration hooks. Those hooks run after every registry is filled, so they also see
what Fabric initializers registered.

| Fabric API | On NeoForge | Notes |
|---|---|---|
| `FabricDefaultAttributeRegistry.register` | `EntityAttributeCreationEvent` | for a type that already has attributes (vanilla's or a NeoForge mod's), applied as a modification instead |
| `FabricDefaultAttributeRegistry.MODIFY` (26.x; not in the 1.21.11 API) | `EntityAttributeModificationEvent` | runs once, after registration, as upstream; attribute registrations made later are ignored with a warning |
| `FabricEntityType.Builder` (`createLiving`, `createMob`, `alwaysUpdateVelocity`, `canPotentiallyExecuteCommands`) | NeoForge's own `EntityType.Builder` methods | default attributes and spawn placements are taken when the type is built |
| Spawn placements | `RegisterSpawnPlacementsEvent` (replace) | |
| `FabricEntityDataRegistry` (`FabricTrackedDataRegistry` on 1.21.11) | NeoForge's `ENTITY_DATA_SERIALIZERS` registry | NeoForge keeps the ids in step between server and client; register from an initializer |
| `FabricBlockEntityTypeBuilder`, `FabricBlockEntityType.addValidBlock` (`addSupportedBlock` on 1.21.11) | NeoForge's op-only constructor; a larger copy of the valid-block set | |
| `WoodTypeBuilder`, `BlockSetTypeBuilder`, `PoiHelper` / `PointOfInterestHelper` | vanilla, unchanged | Fabricated's access transformer opens `PoiTypes.register` and `SpawnPlacements.register`, which the API calls |
| `TradeOfferHelper` (1.21.11) | `VillagerTradesEvent`, `WandererTradesEvent` | NeoForge rebuilds the tables on every data load, so registrations are replayed each time. In rebalanced-trade worlds, additions for professions without their own rebalanced table use the normal ones |
| **Not used**: `MinecartComparatorLogicRegistry` | none | registrations are kept, but detector rails ignore them (NeoForge has no hook) |

Data saved with a null data-fix type, which upstream also handles, already works on NeoForge. The client-side
sign-editing mixins are not shipped: NeoForge resolves modded wood types' textures in the sign-editing screens
itself, except on 1.21.11, where the plain (not hanging) sign screen shows a missing texture for modded wood
types.

CI checks, on all four versions: a mob type built with `createMob` gets its default attributes, the `MODIFY`
change (26.x), its spawn placement and the velocity flag; an entity data serializer gets an id; a block entity type
gets the op-only flag and an added block. On 1.21.11, also a villager trade added through `TradeOfferHelper`.

### Native `fabric-block-api-v1`

Upstream's mixins redirect code NeoForge patched, so Fabricated ships the unchanged public API and answers it from
NeoForge's block hooks.

| Fabric API | On NeoForge | Notes |
|---|---|---|
| `FabricBlock.getAppearance`, `FabricBlockState.getAppearance` | NeoForge's `getAppearance` | the two have the same signature; Fabricated gives `Block` and `BlockState` their own, so a block overriding it overrides both |
| `FabricBlock.getProvidedEnchantmentPower` (26.2+) | `getEnchantPowerBonus` | for blocks in `minecraft:enchantment_power_provider`, as upstream |
| `BlockFunctionalityTags.CAN_CLIMB_TRAPDOOR_ABOVE` | `makesOpenTrapdoorAboveClimbable` | ladders keep NeoForge's facing rule |
| `FabricBlock.FabricProperties.blockId` (26.x) | the properties' id | |
| `FluidFlowEvents.ALLOW` (26.2+) | NeoForge's fluid interaction registry | asked when a neighbour of a liquid block changes, where vanilla makes obsidian or basalt; **not** when a liquid is placed or flows into a new position (NeoForge has no hook there) |
| **Not provided**: upstream's chunk-section air fix (MC-232360) | none | a modded block that reports itself as air may still be treated as air there, as on NeoForge |

A Fabric block class compiled against Fabricated's development setup (as Fabricated's own test mod is) must override
`getAppearance` itself, because the compiler sees both defaults; already-compiled Fabric mods are unaffected.

CI checks, on all four versions: the trapdoor tag, appearance through Fabric's interface, and on 26.x the properties
id; on 26.2 and 26.3 also enchanting power and the fluid flow event.

### Native `fabric-item-api-v1`

Upstream's mixins redirect furnace, brewing, crafting, enchanting, damage and tooltip code that NeoForge patched, so
Fabricated ships the unchanged public API and answers it from NeoForge's item hooks and events. A Fabric item's
overrides of `FabricItem` take effect wherever NeoForge asks.

| Fabric API | On NeoForge | Notes |
|---|---|---|
| `FabricItem.getCraftingRemainder` (`getRecipeRemainder` on 1.21.11) | `getCraftingRemainder` | furnaces, brewing stands, crafting |
| `canBeEnchantedWith`, `EnchantmentEvents.ALLOW_ENCHANTING` | `supportsEnchantment` (anvil, command, loot), `isPrimaryItemFor` (enchanting table) | enchanted books and books keep NeoForge's rule |
| `Properties.equipmentSlot` | `getEquipmentSlot` | NeoForge's hook doesn't say which entity asks, so the provider gets `null` for it |
| `Properties.customDamage` | `damageItem` | not asked for creative players, as upstream; the slot is the one holding the stack |
| `allowComponentsUpdateAnimation`, `allowContinuingBlockBreaking` (client) | `shouldCauseReequipAnimation`, `shouldCauseBlockBreakReset` | |
| `getCreatorNamespace` | `getCreatorModId` | when the item overrides it |
| `DefaultItemComponentEvents.MODIFY` | `ModifyDefaultComponentsEvent` | as a component patch on 1.21.11 |
| `ItemComponentTooltipProviderRegistry` (26.x) | `RegisterTooltipAppendersEvent` | same placements (first, last, before, after) |
| `ComponentTooltipAppenderRegistry` (1.21.11) | `ItemTooltipEvent` | NeoForge 1.21.11 can't place component tooltips, so they come after the other lines; anchors aren't honoured |
| `ItemClickBehaviorCallback` (26.2+) | `ItemStackedOnOtherEvent` | |
| `ItemTooltipCallback` (client) | `ItemTooltipEvent` | |
| `FabricComponentMapBuilder`, `FabricTooltipFlag`, `Properties.itemId`/`modelId` | added by Fabricated's mixins | NeoForge already gives the builder `get` |
| `EnchantmentEvents.MODIFY` (and `MODIFY_WITH_LOOKUP` on 26.x), 26.3's `BlockTransformerEvents.MODIFY` and `BlockTransformerHelper` | Fabricated's hook where vanilla reads each data pack registry entry, as upstream | a changed entry is sent to clients in full |

CI checks, on all four versions: equipment slot, crafting remainder, the allow-enchanting event, custom damage
(through a player), a default component change, the creator mod and an enchantment modified as it loads; on 26.x
also the item id; on 26.3 also a registered block transform and one added by the modify event.

### Native `fabric-events-interaction-v0`

Upstream hooks the server's and client's interaction code with mixins at the same places NeoForge fires its
interaction events, and NeoForge's patches change that code. Fabricated ships the unchanged public API and runs the
callbacks from NeoForge's events, at the highest priority (upstream's hooks come first too). A result other than
`PASS` (or a non-null one for the `BlockEvents`/`ItemEvents` callbacks) cancels NeoForge's event with that result.

| Fabric API | On NeoForge | Notes |
|---|---|---|
| `UseBlockCallback` | `PlayerInteractEvent.RightClickBlock` | both sides; skipped for spectators on the client, as upstream |
| `UseItemCallback` | `PlayerInteractEvent.RightClickItem` | both sides |
| `UseEntityCallback` | `EntityInteract` (26.2+), `EntityInteractSpecific` (26.1.2, 1.21.11), plus `EntityInteract` without a hit result on the 1.21.11 server | as upstream, the hit result carries the clicked point |
| `AttackBlockCallback` | `PlayerInteractEvent.LeftClickBlock` when breaking starts | a cancelled attack resends the block (and its block entity) to the client, as upstream; `BlockAttackInteractionAware` is honoured |
| `AttackEntityCallback` | `AttackEntityEvent` | the hit result is always `null` |
| `BlockEvents.USE_ITEM_ON`, `ItemEvents.USE_ON` | `UseItemOnBlockEvent` (block and item phases) | at the head of `BlockState.useItemOn` and `ItemStack.useOn` |
| `BlockEvents.USE_WITHOUT_ITEM`, `ItemEvents.USE`, `PlayerPickItemEvents` | Fabricated's mixins, as upstream's | NeoForge has no event for these |
| `PlayerBlockBreakEvents.BEFORE`/`CANCELED` | `BreakBlockEvent` (26.x), `BlockEvent.BreakEvent` (1.21.11) | server side, as upstream; `CANCELED` resends the 3x3 area around the block |
| `PlayerBlockBreakEvents.AFTER`, `ClientPlayerBlockBreakEvents.AFTER` | a hook observing the block's removal | |
| `ClientPreAttackCallback` | asked once per client tick; cancels `InputEvent.InteractionKeyMappingTriggered` for attacks | no swing, and block breaking stops, as upstream |
| `ClientHotbarScrollEvents` (26.x) | a mixin around the hotbar slot change, as upstream's | scroll amounts from `InputEvent.MouseScrollingEvent` |
| `FakePlayer` | Fabricated's packet listener; the player reports `isFakePlayer()` | NeoForge then defaults it to survival and (26.2+) gives it a no-op advancement tracker; on 1.21.11 and 26.1.2 upstream's advancement guards apply |

On the client, NeoForge fires these events while it builds the interaction packet, so a client-side cancelled
interaction is still sent to the server, where the callbacks run again. Upstream sends it only when the client
result consumes the action.

CI checks, on all four versions, with a Fabric fake player on the server: every server-side callback above is
called and its result honoured (use item, use block, block use-item-on and use-without-item, item use and use-on,
attack block, attack entity, use entity, break before/canceled/after, pick item), and the fake player is a NeoForge
fake player. The client mixins are applied when the client starts.

### Native `fabric-loot-api-v3`

Upstream's loot builder, accessor and drop mixins pass pre-flight on all four versions, so Fabricated ships them
unchanged (with the public API). Only its hook into loot table loading conflicts with NeoForge, which rewrote that
code; Fabricated replaces it:

| Fabric API | On NeoForge | Notes |
|---|---|---|
| `LootTableEvents.REPLACE`, `MODIFY` | `LootTableLoadEvent` (high priority) | once per table, with the source (vanilla, mod or data pack) of the file in use; the table is rebuilt from Fabric's builder, as upstream |
| `LootTableEvents.ALL_LOADED` | a hook on `ReloadableServerRegistries.reload` | once the reload's loot table registry is complete; the reload's result is passed on unchanged |
| `LootTableEvents.MODIFY_DROPS` | upstream's mixin | NeoForge's global loot modifiers run after it |
| `FabricLootTableBuilder`, `FabricLootPoolBuilder` | upstream's mixins | |

On 26.3, NeoForge's event offers the loading context only as a `HolderGetter.Provider`: the registry lookup Fabric's
events receive there can get entries and tags (loot tables included) but lists none.

CI checks, on all four versions: dirt's table is modified with source `VANILLA`, `REPLACE` and `ALL_LOADED` run, and
dirt's drops contain the added pool's diamond and the drop listener's emerald.

### Native `fabric-content-registries-v0`

Most of upstream's registries and mixins apply on NeoForge as they are and ship unchanged. Where NeoForge rewrote the
code they rely on, Fabricated bridges the gap:

| Fabric API | On NeoForge | Notes |
|---|---|---|
| `CompostableRegistry` (`CompostingChanceRegistry` on 1.21.11), up to 26.2 | NeoForge's composter asks its data map; an item the data map leaves out gets the chance Fabric's registry stored | data packs win; villagers only compost data-map items |
| `VibrationFrequencyRegistry` (`SculkSensorFrequencyRegistry` on 1.21.11) | NeoForge's frequency lookup falls back to the table Fabric's registry writes | data packs win |
| `FuelValueEvents` (`FuelRegistryEvents` on 1.21.11), up to 26.2 | run on NeoForge's fuel builder, after its furnace fuel data map | `BUILD`, then `EXCLUSIONS`; vanilla's non-flammable wood exclusion is already in the data map |
| Flammable, strippable, tillable, flattenable, oxidizable and waxable blocks, path types, villager interactions, pot patterns, brewing | upstream's mixins | NeoForge falls back to the vanilla tables these write; on 26.3, where NeoForge reads oxidation and waxing from its data maps only, Fabricated adds that fallback |
| `EntityFluidInteractionRegistry` (26.x) | upstream's mixins, without three injectors | UNVERIFIED: what the interactions registered here do in a Fabric fluid is not tested. Until Fabric fluids had fluid types (see *Fabric mods' fluids* below), an entity touching one crashed the game; swimming, jumping out of it, drowning and boats floating follow those types now, which NeoForge's rewritten code asks |
| Oxidation and wax scraping (26.3) | added to the axe's block transformer, as upstream | see `BlockTransformerHelper` under `fabric-item-api-v1` |

On 26.3, composting, stripping and fuel are data-driven in vanilla, and Fabric API has no registries for them.

CI checks, on all four versions: a flammable block, a waxable pair and a custom game event's vibration frequency take
effect; up to 26.2 also a compostable item, a strippable block and a fuel value.

### Native `fabric-biome-api-v1` (26.3)

NeoForge reads a biome's climate, environment attributes, effects and generation settings from its own modifiable
biome info, made when the biome is built, and keeps the biome's fields for them private. Upstream's module replaces
those fields: on NeoForge that would change nothing NeoForge reads, and on 26.3, where mob spawns are an environment
attribute, adding a spawn crashed world creation (`IllegalAccessError`, BIOME-1). On 26.3 Fabricated ships the module:

| Part | On NeoForge |
|---|---|
| `BiomeModifications` | applied by a NeoForge biome modifier (`fabricated:fabric_biome_api`, declared by Fabricated's data pack) on NeoForge's builders, as the server starts: additions in NeoForge's `ADD` phase, removals in `REMOVE`, replacements in `MODIFY`, post-processing in `AFTER_EVERYTHING`; selectors see the biome as it was built, as upstream's do |
| Mob spawns and spawn costs | NeoForge's spawn builder, which NeoForge writes into the biome's `NATURAL_MOB_SPAWNS` attribute as an overlay; a mod setting that attribute itself replaces the builder's spawns and costs. For a biome whose spawns aren't an overlay (which NeoForge then leaves alone), the spawns are read from the attribute and set on it, as upstream does |
| `NetherBiomes`, `TheEndBiomes` | upstream's biome source mixins, unchanged |

CI checks, on 26.3: a mob spawn added to plains and plains' changed temperature are in the biome once the server has
started. On 1.21.11, 26.1.2 and 26.2 upstream's module loads: its spawn, effect and feature changes edit objects NeoForge's info
shares, and the attribute map is an ordinary field there (NeoForge's coremod replaces only the climate and the effects,
read in its source for 21.11, 26.1 and 26.2), which the module's widener opens, so they take effect (BIOME-2: Fabricated
kept that field private on every version before, and the module's first attribute change stopped the 1.21.11 server); a weather change replaces the private climate field and
fails when it runs (as read; UNVERIFIED in a game).

### Native `fabric-api-lookup-api-v1` and `fabric-transfer-api-v1`

Both modules are upstream's code. The lookup module and most of transfer's mixins apply on NeoForge unchanged. Three
mixins needed changes:

| Upstream mixin | On NeoForge | Why |
|---|---|---|
| `HopperBlockEntityMixin` | not used | NeoForge's hoppers already move items through its capabilities |
| `SimpleContainerMixin`, `BaseContainerBlockEntityMixin` | rewritten | they defer `setChanged()` until a transaction commits by redirecting a call NeoForge's patches moved; Fabricated uses the `setItem(slot, stack, insideTransaction)` overload NeoForge added for the same purpose |

`ItemStorage.SIDED` and `FluidStorage.SIDED` find vanilla containers, cauldrons, composters, jukeboxes, shelves and
bundles as upstream does, and transactions behave as upstream's.

**Bridge to NeoForge's capabilities.** Fabric storages and NeoForge's item and fluid handlers on blocks see each
other: a NeoForge hopper or pipe moves items and fluids in and out of a Fabric mod's machine, and a Fabric mod's
pipe reaches a NeoForge mod's block. Storages in items do too (`ItemStorage.ITEM` and `FluidStorage.ITEM` with
`Capabilities.Item.ITEM` and `Capabilities.Fluid.ITEM`): a NeoForge machine fills a Fabric mod's tank item, and a
Fabric mod's machine a NeoForge mod's. The item's storage changes the item (an empty cell becomes a full one) through
the other side's access to where it is: NeoForge's `ItemAccess` as a Fabric `ContainerItemContext`, and the other way.

- Transactions are mirrored both ways: a simulation (an aborted transaction) on one side is aborted on the other, and
  nested transactions keep their meaning.
- Fluids convert at 81 droplets per millibucket. Only whole millibuckets move between the two: if a Fabric storage
  would take part of one, it is asked again for whole millibuckets.
- Each side answers for what its own mods add, told apart by namespace. NeoForge code asking about a Fabric mod's
  block or item gets the answer of Fabric's whole lookup, fallbacks included: Tech Reborn offers its machines' tanks
  and energy through one, and a Fabric mod's `Container` block entity gets Fabric's generic wrapper, as on Fabric.
  Fabric code asking about a NeoForge mod's block or item gets its NeoForge capability, before Fabric's generic
  `Container` wrapper, so the block's access rules hold. Neither side ever asks the other about the same thing.
- A NeoForge mod's own capability for a Fabric mod's block or item wins over the Fabric storage for NeoForge code;
  Fabric code doesn't see it. Content a Fabric mod registers under another mod's namespace counts as that mod's.
- Vanilla blocks and items keep each side's own wrappers. Every bucket item gets Fabric's own bucket storage from
  upstream's fallback, which comes before NeoForge's capability.
- A storage on one side that uses one on the other in the same transaction (an item's handler changing the item
  through the other side's access, a NeoForge handler passing items on to a Fabric storage) works: where a
  transaction already stands for one of the other side's, it is used instead of a new one nested in it.
- Entity storages are not bridged: Fabric API has no lookup for them.

**Energy, with Team Reborn's library.** Fabric API has no energy API; Fabric tech mods share Team Reborn's
(`team_reborn_energy`, nested in the mods that use it). When a Fabric mod brings it, its storages and NeoForge's
energy handlers see each other the same way, on blocks (its `SIDED` lookup and `Capabilities.Energy.BLOCK`) and in
items (its `ITEM` lookup and `Capabilities.Energy.ITEM`, batteries charged through either side). One unit is one
unit: neither API fixes an exchange rate. Without the library nothing of this loads.

On 1.21.11, `FluidVariantRendering`'s default sprites and colours come from NeoForge's fluid client extensions instead
of `fabric-rendering-fluids-v1`'s registry.

CI checks, on all four versions: a chest found through `ItemStorage.SIDED` keeps its items when a transaction is
aborted and changes when one commits; a full water cauldron empties when a bucket of water is extracted; a Fabric
storage used through NeoForge's capability and a NeoForge handler used through `ItemStorage.SIDED` follow aborts,
nested commits and commits; fluids move in whole millibuckets only; NeoForge code fills a Fabric mod's fluid cell
in a slot, and Fabric code a NeoForge mod's, each item swapped only when the transaction commits. With Team Reborn's library
(fetched for the server run with Fabric API where Modrinth has a build, of its own or nested in Tech Reborn's or
Modern Industrialization's jar): NeoForge code charges a Fabric mod's energy block and Fabric code a NeoForge mod's,
an aborted transaction leaving each unchanged; the Fabric block's energy comes from a lookup fallback, as Tech
Reborn's machines' does.

### Fabric mods' fluids

NeoForge asks every fluid for its fluid type: to move entities in it, to let it meet lava, to name it in a tank, and
on 1.21.11 to draw it. A mod's fluid without one throws (`Mod fluids must override getFluidType`), and no Fabric fluid
has one, so the first entity touching it, neighbour changing next to it or NeoForge tank naming it crashed the game.
Fabricated gives each Fabric fluid family (a source and its flowing fluid) one type, registered under the source's
id, which answers from Fabric's side when asked:

| NeoForge asks | Answer |
|---|---|
| name, light, temperature, viscosity, lighter than air, bucket sounds | Fabric's `FluidVariantAttributes` for the source (its handler, or Fabric's defaults: the fluid block's name and light) |
| water-like, pushes entities, swimming, drowning, putting out fire, hydrating farmland, boats | yes for a fluid tagged `minecraft:water`, no for any other, as vanilla decides by that tag |
| fall distance | reset in a water-tagged fluid, kept in any other |
| path types | vanilla's, which go by the same tags |

A fluid that isn't water to entities leaves them alone, as on Fabric: a living entity in it moves as through air
(NeoForge would move it with the fluid's own movement, which a type leaves to its mod: none on 26.2 and later, so the
entity would hang where it is, and lava's on 1.21.11), and an item falls through it.

- Not like vanilla: a fluid tagged `minecraft:lava` isn't lava to entities (NeoForge keys burning and lava's movement
  to its own lava type), and an entity jumping inside a fluid that isn't water rises a little, as NeoForge lets it in
  any mod fluid.
- On 1.21.11, NeoForge's renderer and `FluidVariantRendering` take a fluid's textures and tint from its type's client
  extensions; for a Fabric fluid those are its `fabric-rendering-fluids-v1` render handler's, and water's without one
  (as vanilla draws such a fluid). On 26.x vanilla draws fluids from fluid models, and the type plays no part.
- Fabric's `FluidFlowEvents.ALLOW` (26.2+) is asked for Fabric fluids too, now that they have types to hook.
- A fluid registered after the registry events (from a client initializer, say) gets an unregistered type when first
  asked, without client extensions.

CI checks, on all four versions: two test fluids, one tagged `minecraft:water`; a family's source and flowing fluid
share one type, registered under the source's id, with the block's light and name, water-like only for the tagged
one; a placed fluid block asks NeoForge's fluid interactions about itself without crashing; a pig falls through a
column of the untagged fluid as through air.

### Native `fabric-menu-api-v1` (`fabric-screen-handler-api-v1` on 1.21.11)

NeoForge replaced the `openMenu` upstream's mixins target with its own, which already lets a menu provider send
extra data to the client. Fabricated ships upstream's API and maps it onto that:

| Fabric API | NeoForge |
|---|---|
| `ExtendedMenuProvider.getScreenOpeningData` | written where NeoForge asks the provider for its client data (`writeClientSideData`), so the menu opens through NeoForge's extra-data packet |
| `ExtendedMenuType` creating the client menu from that data | NeoForge's `MenuType.create(id, inventory, data)` on the client |
| `FabricMenuProvider.shouldCloseCurrentScreen` | combined with NeoForge's `shouldTriggerClientSideContainerClosingOnOpen` |
| a `SimpleMenuProvider` around an `ExtendedMenuProvider` | opens the extended menu, as upstream |

Fabric mods' menus therefore open the same way NeoForge mods' menus do. The client CI run opens an extended
menu with opening data from the integrated server.

### `fabric-rendering-v1`, `fabric-screen-api-v1` and `fabric-sound-api-v1`

Upstream's module loads, without the mixins that target code NeoForge rewrote:

- **HUD** (`HudElementRegistry`): NeoForge draws the HUD as layers, so upstream's HUD mixin can't apply.
  Fabricated wraps each of NeoForge's vanilla layers with the Fabric element list of the same vanilla element, so
  elements attached before or after a vanilla element, replaced or removed, behave as on Fabric. Its wrappers
  run after NeoForge mods registered their layers, so the Fabric elements surround whatever those mods did to the
  vanilla layers. On 1.21.11 the deprecated `HudRenderCallback` fires from NeoForge's `RenderGuiEvent.Post`, at the
  end of the HUD as upstream does.
- **Block colors** (1.21.11): NeoForge keeps block color providers in a map by block instead of vanilla's
  id-indexed one; `ColorProviderRegistry.BLOCK.get` reads NeoForge's map.
- **Picture-in-picture renderers** (`PictureInPictureRendererRegistry`): NeoForge rebuilt the GUI renderer around
  its own renderer pools. On 26.2 and 26.3, Fabricated registers the renderers Fabric mods registered with NeoForge,
  which creates and pools them as it does its own. On 1.21.11 and 26.1.2, Fabric's renderers need the game
  renderer's submit queue, which doesn't exist yet when NeoForge gathers its renderers, so they are not used.
- **Debug options screen**: keeps vanilla's sorting and search instead of upstream's namespace-aware ones.
- **Render pipeline snippets** (1.21.11, 26.1.2, 26.2): NeoForge added a field to them, so they don't carry Fabric's
  "use the pipeline's draw mode in GUIs" flag; pipelines still do.

The rest of the module (world rendering events, block and item colors, entity and block entity renderers, model
layers, tooltip components, render pipelines and so on) is upstream's code, unchanged.

`fabric-screen-api-v1` (all versions) loads without upstream's wrapper around drawing a screen: Fabricated
fires `ScreenEvents.beforeExtract` and `afterExtract` (`beforeRender` and `afterRender` on 1.21.11) from NeoForge's
`ScreenEvent.Render.Pre` and `Post`, which NeoForge posts around the same call. They run right around the screen (after NeoForge mods' `Pre` listeners, and not when
one of them cancels drawing the screen; before their `Post` listeners). The module's other hooks (screen init,
removal, ticks, mouse and keyboard events) are upstream's.

`fabric-rendering-fluids-v1` loads without the handler that changes which neighbouring blocks show a fluid's
overlay texture: NeoForge asks the block (`shouldDisplayFluidOverlay`) instead, so blocks marked transparent
through `FluidRenderingRegistry.setBlockTransparency` keep NeoForge's behavior. On 1.21.11, NeoForge also tints
fluids through their fluid type; a Fabric fluid render handler's `getFluidColor` replaces that tint's color (its
alpha stays NeoForge's). Custom fluid renderers and sprites work.

`fabric-sound-api-v1` (all versions) loads without upstream's redirect of the audio stream lookup, which NeoForge replaced with
its own `SoundInstance.getStream`. A sound instance whose class overrides `FabricSoundInstance.getAudioStream`
gets its stream from it there; other sounds keep NeoForge's path.

### `fabric-renderer-api-v1`, `fabric-renderer-indigo` and `fabric-model-loading-api-v1`

The renderer API loads with Indigo as its renderer, so meshes, quad emitters and Fabric's model types work. A block
whose model implements `emitQuads` itself (a Fabric model), or whose vanilla multipart or weighted model has a Fabric
model among its parts, is drawn by Fabric's renderer: in the world (into the chunk's buffers), as a moving block
(pushed by a piston, falling) and with the breaking overlay. Every other block keeps NeoForge's renderer, so NeoForge's
model data, tints, face culling and ambient occlusion per part still apply to it; upstream's hooks that send *every*
block through Fabric's renderer in those places are left out for that reason. Block outlines, and the glow outline of a
moving block, still come from a model's parts as NeoForge sees them. NeoForge already gives vanilla's models
`createGeometryKey` and `particleMaterial` with Fabric's signatures, so upstream's copies are left out, and
`BlockStateModel`, which then inherits both interfaces' defaults of each, declares NeoForge's (a default method
bridge, as for any added interface). On 26.1.2, where upstream's check of a mesh
model's sprites takes the parameters of vanilla's `SimpleModelWrapper.bake` (NeoForge moved the code into its own
overload), a mesh model using sprites from outside the block atlas is not rejected; on 26.2 and 26.3 the check
follows the code into NeoForge's overload. On 26.1.2 and 26.2 the block overlay seen from inside a block uses the
block's plain particle sprite.

Model loading loads too: its plugins, model modifiers and extra models work, with the extra models added to
NeoForge's model discovery by Fabricated (NeoForge rewrote the code upstream hooks). Fabric's custom model formats
work next to NeoForge's: a model file with a `fabric:type` goes to the deserializer a Fabric mod registered with
`UnbakedModelDeserializer`, and a blockstate model with a `fabric:type` to the codec registered with
`CustomUnbakedBlockStateModel`, while NeoForge's own model loaders (`loader`) and blockstate model types (`type`, such as
`neoforge:composite`) keep working. Upstream's mixin that replaces the blockstate model codec is left out; Fabricated
adds Fabric's types to the codec NeoForge builds instead.

When Fabric's renderer draws a model that has no `emitQuads` of its own (a NeoForge model a Fabric model wraps, or
every model under a renderer that meshes the world through Fabric's renderer API), its parts come from NeoForge's
level-aware `collectParts`, so connected textures and other model-data models keep working, and quads keep NeoForge's
per-part and per-quad ambient occlusion (the defaults Forgified Fabric API has; upstream's ignore both).

On 1.21.11 the renderer API loads with Indigo too, and Fabric block models, alone or among the parts of a vanilla
multipart or weighted model, are drawn by Fabric's renderer in the chunk sections NeoForge compiles, as on 26.x; CI
checks both. Everywhere else NeoForge asks a model for its parts (its level-aware `collectParts`), a Fabric model gives
parts made from the quads it emits (since this pass): one part per render layer, each quad with the colours, normals,
light emission and ambient occlusion flag NeoForge's quads carry. So these see Fabric models:

- moving blocks (pushed by a piston, falling), the breaking overlay and blocks drawn as entities (`renderSingleBlock`:
  a minecart's contents, an enderman's block);
- NeoForge's translucent block outline pass, and other mods that draw blocks from their parts;
- renderer mods that mesh chunk sections themselves: Sodium for NeoForge (see *Renderer mods* below).

CI checks the parts (a NeoForge quad's baked colour kept through them), a falling block and the breaking overlay. A
model asked for its parts while it emits keeps the parts of its vanilla method, as does a caller passing air
(NeoForge's deprecated overloads). Also on 1.21.11:

- The renderer API's `emitQuads` defaults are NeoForge-aware, as on 26.x (since this pass): a NeoForge model inside a
  Fabric model gets its parts from NeoForge's level-aware `collectParts`, with NeoForge's ambient occlusion and its
  quads' baked colours and normals. CI checks the level and the colours.
- Upstream's mixins that NeoForge's 1.21.11 build conflicts with are left out (see the module's plan): vanilla models'
  `createGeometryKey` is NeoForge's, a mesh model using sprites from outside the block atlas isn't rejected, and the
  in-wall screen overlay uses NeoForge's level-aware particle icon. The model baker's sprite getter gets Fabric's sprite
  finders from Fabricated, as upstream's mixin reads them from fields NeoForge's build names differently.

Model loading works as on 26.x: plugins, model modifiers, extra models and Fabric's model formats. Upstream's hook that
hands the baked extra models to the model manager can't apply to NeoForge's 1.21.11 build (it looks for a local Mixin
doesn't find there), so Fabricated hands them over from NeoForge's bake event call.

### Renderer mods: Sodium and Iris (26.x)

Use the NeoForge builds of Sodium and Iris. Their Fabric builds are disabled with a message: Sodium's would mesh
NeoForge's models, fluids and lighting the Fabric way and drop the geometry NeoForge mods add to chunks, and Iris's
needs Sodium's Fabric build. The mixed pairs are refused even with `loadUnsupported`: Iris's Fabric build next to
Sodium for NeoForge, and Sodium's Fabric build next to Iris for NeoForge, as each hooks classes only its own partner
build has (their fluid renderers). Sodium for NeoForge carries its own implementation of Fabric's renderer API and switches
it on whenever Fabric API's renderer module is loaded, which Fabricated with the Fabric API jar provides. It then meshes
every block through that renderer, so Fabric block models show in the world. Fabricated's part:

- It sees Sodium, whose mod is nested inside a service jar, and reads its mod properties, so Fabric API's own renderer
  (Indigo) stands down for Sodium's, and Fabric mods depending on `sodium` or on `indium` (which Sodium provides) load.
- Its renderer API defaults keep NeoForge models' level, model data and ambient occlusion when Sodium draws them
  through Fabric's renderer (connected textures and other model-data models keep working).
- Moving blocks and the breaking overlay of Fabric models go through the registered renderer, Sodium's.
- A Fabric mod's Sodium integration is given to Sodium for NeoForge in the form it reads: its page in Sodium's options
  (the `sodium:config_api_user` entrypoint, a plain class) and its overrides of Sodium's mixin options
  (`sodium:options`, see *Custom values* below).

CI runs Sodium and Iris for NeoForge (from Modrinth) with Fabric API and a Fabric model test mod on 26.1.2, 26.2 and
26.3, and checks each of these. Shaders weren't enabled in that run. A Fabric addon's mixins into Sodium are checked
against the installed Sodium for NeoForge before the game starts, like mixins into the game: one that needs Sodium's
Fabric-only classes, or members its NeoForge build lacks, is reported and the addon disabled instead of crashing.

On 1.21.11 Sodium for NeoForge meshes chunks itself and has no Fabric renderer, though its metadata says it contains
one (`fabric-renderer-api-v1:contains_renderer`, read in its 1.21.11 source), so Indigo stood down and Fabric's
renderer API had no renderer: Fabric block models stayed invisible and a Fabric mod building a mesh would fail. Once
the client initializers ran, Fabricated registers Indigo's renderer when none registered (on every version; Indigo's
own hooks stay off then), and Sodium draws a Fabric block model from the parts Fabricated makes from its quads.
CI's informational 1.21.11 run with Sodium for NeoForge saw the test model and the multipart part with it meshed in
Sodium's own section level (run 374; run 372, without the fallback, had no parts).

### Plugins for recipe viewers, tooltip mods and Simple Voice Chat

A Fabric mod's plugins for JEI, REI, Jade, EMI and Simple Voice Chat are its entrypoints (`jei_mod_plugin`,
`rei_common`, `rei_client`, `rei_server`, `jade`, `emi`, `voicechat`); their NeoForge builds look for an annotation on
the plugin class instead. Fabricated adds
that annotation to the mod's NeoForge scan data for each such entrypoint, so the NeoForge builds find the plugins.
WTHIT needs nothing: on NeoForge it reads the jar's `waila_plugins.json` / `wthit_plugins.json`, which stay in the
translated mod. Jade's per-mod settings (the `jade` custom value) reach Jade for NeoForge as a mod property (see
*Custom values* below). Which entrypoint becomes which annotation, or which mod property, is data: core's
`interop.json`, one line per entrypoint key.

- The NeoForge builds create a plugin from a class with a public no-argument constructor, as Fabric's default
  language adapter does with a class name. A plugin given as a field or method reference (`Class::member`) or through
  another language adapter (Kotlin's `object`) is left out, with a line in the report.
- JEI and REI create plugins while NeoForge constructs mods, before Fabric `main` entrypoints run (on Fabric JEI does
  it after them); EMI and Jade create a plugin again on each reload.
- A plugin that calls a viewer's Fabric-only API (`mezz.jei.api.fabric`, EMI's `FabricEmiStack`) fails at that call.
- CI runs the NeoForge builds of JEI, REI, Jade, WTHIT and Simple Voice Chat (from Modrinth, where one exists for the
  version) with the test mod's plugins (informational). EMI has no NeoForge build for these versions (UNVERIFIED), so only its mapping is
  unit-tested.

### Custom values

Libraries built for both loaders read a mod's settings from its `fabric.mod.json` custom values on Fabric, and from its
`mods.toml` on NeoForge, under the same name. Every custom value of a Fabric mod becomes a mod property of the
translated mod (`[modproperties.<id>]`, where Jade for NeoForge reads `jade`), and every namespaced one (`ns:key`) is
also a key of the file and of the mod's `[[mods]]` entry: Sodium for NeoForge reads `sodium:options` from the entry,
Lithium for NeoForge `lithium:options` from the file. No key of NeoForge's own has a colon, so these never clash with
it. Nulls are left out, as TOML has none. Unit tests read the generated file with night-config, the parser NeoForge
uses, and find each value where those mods look.

### Fabric builds that can't run on NeoForge

A few mods' Fabric builds can't work on NeoForge whatever Fabricated does; these facts are data (core's
`compat-plan.json`). Such a build is disabled with the reason and what to do instead, and the mods that need it with
it; `loadUnsupported` loads it anyway, for testing.

A build for another Minecraft version that Fabricated can tell can't run is disabled too, with the build to install:
on 26.x, one whose mixins name intermediary classes (`net.minecraft.class_1234`), the names obfuscated versions (1.21.11
and older) run Fabric mods with, which 26.x doesn't have (OBF-1: Modrinth lists some 1.21.11 builds for 26.x too).

- **Sodium and Iris**: their NeoForge builds are used (see *Renderer mods* above).
- **Essential** (`essential-container`, and `essential-loader`, which partner mods nest): its Fabric build is a loader
  that downloads Essential when the game starts and adds it to Fabric Loader's internal mod list, class loader and
  Mixin's processor (read in Essential Loader's source), none of which exist on NeoForge. Essential has NeoForge
  builds for some versions.
- **malilib** (and with it Tweakeroo, MiniHUD and Litematica): it adds its picture-in-picture renderers through a map
  NeoForge replaced with renderer pools on every version, and calls that accessor while the game starts (CORPUS-13).
- **WTHIT**: its Fabric build refuses NeoForge itself; its NeoForge build is the one to use.

### Fabric Loader

The public `net.fabricmc.loader.api` is complete: entrypoints (including custom ones and language adapters such as
Kotlin's), mod containers and metadata, versions and predicates, the object share, and the mapping resolver
(intermediary to Mojang names on 1.21.11). As on Fabric, every declared language adapter is created before any Fabric
mod's mixin plugin loads (some mods declare one only to run code that early). `preLaunch` entrypoints still run later
than on Fabric: right before `main`.

Every mod has a root path, as on Fabric (mods take the first without checking): Minecraft's is the game's file, Java's
its home, the mods Fabricated provides (Fabric Loader, the native Fabric API modules) have Fabricated's in-game mod,
MixinExtras has NeoForge's jar, and a NeoForge mod nested in another jar has that jar.

- While Fabric entrypoints run, the thread's context class loader finds a Fabric mod's directories (a package's,
  as a `jar:file:` URL into its jar), as Fabric Loader's class loader does; NeoForge's finds only files. Mods that
  find their classes by scanning a package (Reflections, which YUNG's API uses for what it registers) found nothing
  without it (SCAN-1). A scan outside an entrypoint, on another thread or later, still finds nothing.
- A file read through one of the mod's classes (`Class.getResourceAsStream`, `Class.getResource`) is looked up in the
  mod's own jar first and then, as Fabric's unnamed modules do, on the whole class path: NeoForge's module for the mod
  has only its own files, so a mod reading Minecraft's `/version.json` that way (Lattice, while Mixin prepares its
  config) stopped the game (RES-1). The mod's calls go through Fabricated's `ClassResources`.
- On a client, `main` and `client` entrypoints run where Fabric Loader runs them: in `Minecraft`'s constructor, once
  the instance exists, with the registries open (CORPUS-16). NeoForge registers content before `Minecraft` exists, so
  what it fills once its registration events end takes what Fabric mods registered there too: default attributes,
  spawn placements, fluid types, the creative tabs' order, Forge Config API Port configs (loaded as they are
  registered, as the port does on Fabric) and tooltip providers (placed before or after all component tooltips). A
  NeoForge mod that reads the registries during NeoForge's own registration events doesn't see Fabric mods' content
  on a client. On a dedicated server `main` and `server` entrypoints run while NeoForge registers content, as before.
- A Fabric payload type registered after NeoForge's payload registration (MiniHUD's, from the end of `Minecraft`'s
  constructor; Ledger's, as the server starts) is registered with NeoForge then, as upstream accepts it at any time
  (NET-3). An id a NeoForge mod already registered stays the NeoForge mod's.
- `@Environment` and `@EnvironmentInterface` are applied as Fabric Loader applies them: members and interfaces for
  the other side are stripped when the class loads, so a common class implementing a client-only interface loads on
  a dedicated server.
- Of Fabric Loader's internals, `FabricLoaderImpl.INSTANCE` (its public API methods) and
  `FabricLauncherBase.getLauncher()` (environment, development flag, runtime namespace) are provided for the mods that
  use them. A jar a mod adds to the class path while running (`addToClassPath`, or the legacy launcher's `propose`,
  through which LuckPerms adds the libraries it downloads) is found for the classes no mod's module has, untransformed
  (LOADER-2). `ModContainerImpl`, `EntrypointStorage` and `EntrypointMetadata` exist so that code naming them links
  (FrozenLib's entrypoint injector, from a mixin plugin): there is no instance of the first two to reach, so such code
  takes the fallback it keeps for a loader without them (LOADER-1). Other internal classes are not provided. A mod's code that reaches for another one, or for a member of Fabric
  Loader's API that Fabricated lacks, is listed among the report's linkage warnings before the game starts.
- A registry a mod creates through vanilla's own code (`BuiltInRegistries`) before NeoForge's registration has ended
  joins the root registry, which NeoForge froze after its new-registry event, as Fabric API's `FabricRegistryBuilder`
  registries do; an entry a mod registers in a vanilla registry before NeoForge's registration events is accepted, as
  on Fabric (REG-3: Wover, BetterX's library).
- A mod whose required mixin (in a required config without a plugin) calls into a Fabric mod that isn't loading is
  disabled, with the reason: Mixin would stop the game looking for that mod's class while applying it.
- Mods detect Fabricated with `FabricLoader.getInstance().isModLoaded("fabricated")`; see [API.md](API.md).
- `mixinextras` is provided at the version NeoForge runs, as Fabric Loader provides its own, so mods requiring it
  load (copies nested in mod jars are dropped for NeoForge's).
- Forge Config API Port (a copy of NeoForge's config system for Fabric, which many mods use) is replaced by its
  Fabric API on NeoForge's own config system: `ConfigRegistry` registers with the mod's NeoForge container under the
  port's file names, `ModConfigEvents` fire from NeoForge's config events, and `ConfigScreenFactoryRegistry` screens
  open from NeoForge's config button. As on Fabric, a config other than the per-world one can be read as soon as it is
  registered (from its file, before NeoForge loads it). The port's jar is skipped (and provided at its version); its
  copy of Forge's own config API (`ForgeConfigSpec`) is not provided. On 26.3, whose FML renamed the config types `COMMON` and `SERVER` to
  `LOCAL` and `SYNCED` (the port keeps the old names), a mod's references to the old names are renamed. The port's `ConfigurationScreen` takes a mod id where NeoForge's takes the mod's container, so a mod creating one passes its container instead.
- Class tweakers v2 load: their `extend-enum` lines only declare the constants a mod's enum mixins add, which Mixin
  adds itself (0.17.1 or later, so on 26.x; not to NeoForge's own extensible enums).
- An enum mixin that only adds constants to one of NeoForge's extensible enums (which NeoForge lets only its own enum
  extensions change: FML refuses an enum mixin into one) becomes an enum extension NeoForge's loader applies, when each
  constant's name starts with the
  mod's id (Farmer's Delight's recipe book type). Constant arguments are written into the extension; arguments the
  mixin computes when the class loads (a registered object, a static array) come from a method Fabricated generates
  next to the mod's mixins, which runs the mixin's code for them when NeoForge adds the constant (ENUM-3). Where
  NeoForge reserves the mixin's constructor for one taking suppliers (`Raid.RaiderType`, which Friends and Foes extends
  with its raiders), the values are handed over in suppliers. Code that branches, uses local variables, or reads the
  mixin's or the enum's own members isn't moved, and its enum mixin disables the mod, with the reason, as other enum
  mixins into those enums do.
- An enum mixin into one of NeoForge's extensible enums that the enum extension file can't carry (a constant named
  otherwise than the mod's id and a colon, or with a body of its own) becomes an ordinary mixin into the enum's class
  initializer that makes the constants with the enum's constructor and appends them to its values, as mods' own NeoForge
  builds do (ENUM-5). A body overriding the method the enum forwards to a delegate (`GrassColorModifier`'s
  `ColorModifier`, which Wilder Wild's tundra grass colour overrides) becomes a delegate class running it. Code that
  branches or uses the mixin isn't moved, nor constants the mod's classes read as fields of the enum; such a mixin still
  disables its mod. UNVERIFIED in the game.
- Config screens a Fabric mod offers to Mod Menu (its `modmenu` entrypoint) open from the Config button of NeoForge's
  mod list. Mod Menu's API interfaces are provided, so such mods load without Mod Menu, which itself can't run on
  NeoForge (it would duplicate NeoForge's mod list): an installed Mod Menu jar is skipped and provided at its version.
- Where an interface a Fabric mod adds to a game class (with a mixin or a class tweaker) brings a default method the
  class already has from one of NeoForge's interfaces, with the same name and descriptor, the class declares that
  method, calling NeoForge's default, unless a mixin declares it (Fabricated's own do for some of Fabric API's). The JVM can't choose between two such defaults and throws
  (`IncompatibleClassChangeError: Conflicting default methods`); Entity Culling 1.11's `getRenderBoundingBox` on block
  entity renderers was one.
- `PlayerList.getPlayers()` follows the list in the field, as in vanilla. NeoForge returns an unmodifiable view made
  when the player list is constructed, so a mod whose mixin replaces the list (written for vanilla, where both are one
  object) would leave it on the old, empty list, and an integrated server finding no player stays paused. The view is
  still unmodifiable; Fabricated logs a warning once when it sees a replaced list.

### Mixins NeoForge's code can't take (LEAVE-1)

A Fabric mod's mixin can need something NeoForge's build of the game doesn't have: a call NeoForge replaced with its
own hook, a method whose signature NeoForge changed, a library class NeoForge loads without mixin support. Mixin would
stop the game on it, so pre-flight catches it before launch, and what happens next depends on whether the mod's own
code can do without that mixin:

- **A self-contained mixin** adds no interface to its target and has no accessor or invoker (an interface mixin, no
  abstract method either), so nothing outside it calls into it. The mod loads without the failing part: only the injectors that fail when the mixin adds no fields
  of its own (Balm's `LivingEntityMixin` keeps its damage, heal and effect events and loses the one hook into
  `removeAllEffects`, at a `Map.clear()` call NeoForge's code doesn't make there), or the whole mixin when it does, as its injectors may share those
  fields. The features the left-out part hooks in for don't work. The report lists each one under "Mixins left out
  (NeoForge's code differs where they hook in)", with what NeoForge's code lacks; the mod's row (and
  `/fabricated why <mod>`) gives the count, and the log names the mods at startup.
- **Any other mixin**: the mod's own code casts its target to the interface the mixin adds, or calls its accessors,
  so leaving the mixin out would only move the crash into the mod. The mod is disabled with the reason, as are the
  enum mixins NeoForge's extensible enums don't allow.
- In a config with a plugin, which may disable mixins itself, only the failing injector is left out.

A mod disabled this way can still be loaded without the mixin, by naming it in `skipMixins` in
`config/fabricated.json` (the report's suggestion gives the exact entry).

### What this means for Fabric mods

A Fabric mod works today if it needs only the Fabric Loader API and the **native** or **loads** modules
above. Many content mods also need item, block, entity or loot modules that still fail pre-flight, so they are
disabled with a message naming the missing module.

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
4. ✅ `fabric-registry-sync-v0` on all four versions (done). Unblocked debug-api on 26.x. object-builder
   now fails on its own mixins (see above) and is a candidate for a native implementation.
5. ✅ `fabric-entity-events-v1` on all four versions (done).
6. ✅ `fabric-object-builder-api-v1` on all four versions (done). Unblocked `fabric-data-attachment-api-v1`.
7. ✅ `fabric-item-api-v1` and `fabric-block-api-v1` on all four versions (done). The modules that build on them
   (transfer, content registries) still fail on their own mixins.
8. ✅ `fabric-events-interaction-v0` on all four versions (done).
9. ✅ `fabric-loot-api-v3` on all four versions (done).
10. ✅ `fabric-content-registries-v0` on all four versions (done).
11. ✅ `fabric-transfer-api-v1` and `fabric-api-lookup-api-v1` on all four versions (done).
12. ✅ The bridge between Fabric storages and NeoForge's item and fluid capabilities (done).
13. ✅ `fabric-menu-api-v1` / `fabric-screen-handler-api-v1` on all four versions (done).

## Fabric mods tested

CI checks Fabricated's own test mods as gates on every version. Real mods run in these CI runs too, downloaded from
Modrinth in CI (never redistributed):

- **A corpus of popular Fabric mods** (`ci/mod-corpus.txt`: libraries, interface, rendering, performance and content
  mods, with their dependencies), on a server and a client, on every version. Informational: each run's report lists
  which load and why the others are disabled. Known problems are tracked as CORPUS-n in
  [roadmap.md](roadmap.md#findings-log).
- **An advanced corpus of Fabric mods with deep hooks** (`ci/mod-corpus-advanced.txt`: world generation, gameplay
  systems, camera, sound, replays, server tools), on a server and a client of its own, so that one stopping the game
  can't hide the main corpus's results. Informational, as the main corpus is.
- **Sodium and Iris for NeoForge** with Fabric API and the renderer test mod on 26.x: gated (see *Renderer mods*).
  Their Fabric builds run with `loadUnsupported`, informational: since the constructor repair they reach a world and
  mesh Fabric models on 26.1.2 and 26.3.
- **The NeoForge builds of JEI, REI, Jade, WTHIT and Simple Voice Chat**, pinned per version, with the test mod's
  plugins declared the Fabric way: gated. Each creates and calls its plugin on every version (WTHIT has no 26.3 build).
- **OneConfig and PolyPlus** (Fabric only), informational, where Modrinth has builds for the version. Their nested
  Kotlin, Compose and MixinSquared jars split packages between jars, which Fabricated now serves from one module.

To report a result for another mod, see [CONTRIBUTING.md](../CONTRIBUTING.md#reporting-a-compatibility-result).
