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
| 1.21.11 | 21.11.45 | 0.141.6+1.21.11 | ✅ all 35 server and 21 client checks | ✅ boots, all test-mod checks still pass |
| 26.1.2 | 26.1.2.116 | 0.155.3+26.1.2 | ✅ all 35 server and 21 client checks | ✅ boots, all test-mod checks still pass |
| 26.2 | 26.2.0.90 | 0.161.0+26.2 | ✅ all 35 server and 21 client checks | ✅ boots, all test-mod checks still pass |
| 26.3 (beta) | 26.3.0.69-beta | 0.162.0+26.3 | ✅ all 35 server and 21 client checks | ✅ boots, all test-mod checks still pass |

"Boots with Fabric API" means the server starts with the full official Fabric API jar installed. Modules
that can't work on NeoForge are disabled before any code runs (see below), and the rest load alongside
Fabricated's test mods.

The server checks: the server starts; a `main` entrypoint runs; the Fabric Loader API works; a custom
entrypoint resolves; content registers directly into vanilla registries; `fabric-api-base` events fire; an
access widener applies; a `server` entrypoint runs; a mixin applies; a mod with a missing dependency is
reported, naming the dependency; a mod with an impossible mixin is caught by pre-flight; a mod whose
entrypoint throws is isolated while the server keeps running; Fabricated's public API answers and its
after-initializers callback runs; and a client-only interface listed in `@EnvironmentInterface` is stripped on
the server. The other 21 cover the native Fabric API modules (described in their sections below). The client
checks: the client starts and joins a singleplayer world, the `main` and `client` entrypoints run, the
client-side native modules work, including a networking round trip and a configuration task with the integrated
server, an extended menu opens with its data, and a Mod Menu config screen factory is registered with NeoForge.

CI also starts the client with the full Fabric API jar, for information only (it doesn't fail the build). It
joins its world and passes every client check on all four versions. On 26.x every Fabric API module loads
on the client except model-loading, which needs the unsupported renderer-api; on the dedicated server no Fabric
API module is disabled. On 1.21.11, tag-api is still disabled on both sides, and rendering-v1 and
rendering-fluids on the client.

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
| fabric-biome-api-v1 | loads | loads | loads | loads |
| fabric-block-getter-api-v2 / block-view-api-v2 | loads | loads | loads | loads |
| fabric-message-api-v1 | loads | loads | loads | loads |
| fabric-rendering-fluids-v1 | loads (server only; disabled on the client) | loads | loads | loads |
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
| fabric-tag-api-v1 | disabled | loads | loads | loads |
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
| fabric-rendering-v1 on the client | disabled | loads (see below) | loads (see below) | loads (see below) |
| fabric-screen-api-v1, sound-api-v1 on the client | loads (see below) | loads (see below) | loads (see below) | loads (see below) |
| fabric-model-loading-api-v1 on the client | disabled (needs renderer-api) | disabled (needs renderer-api) | disabled (needs renderer-api) | disabled (needs renderer-api) |
| fabric-renderer-api-v1, fabric-renderer-indigo | unsupported | unsupported | unsupported | unsupported |
| fabric-data-generation-api-v1, gametest, client-gametest | unsupported | unsupported | unsupported | unsupported |

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
  loads on all versions, tag-api on 26.x (on 1.21.11 it also reads a synthetic field NeoForge's build names
  differently).
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
| Block entity load/unload | `ChunkEvent.Load/Unload` | only for block entities of chunks that load or unload, not ones placed or removed in a loaded chunk |
| Sync data pack contents | `OnDatapackSyncEvent` | |
| Tags loaded | `TagsUpdatedEvent` | |
| Client: ticks, chunks, entities, block entities | `ClientTickEvent`, `LevelTickEvent`, `ChunkEvent`, `EntityJoin/LeaveLevelEvent` | |
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
`DISCONNECT` events do not fire. A payload id can use a different codec in each direction, as on Fabric
(NeoForge keeps one codec per id, so Fabricated supplies the clientbound one when NeoForge looks it up).
CI checks that a Fabric payload and receiver register and reach NeoForge on a dedicated server, and, on the
client, a play-phase round trip and a configuration task with the integrated server.

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
| **Not fired**: `EnchantmentEvents.MODIFY`/`MODIFY_WITH_LOOKUP`, 26.3's `BlockTransformerEvents` and `BlockTransformerHelper` registrations | none | they change data-pack entries while they load; NeoForge has no hook for that yet |

CI checks, on all four versions: equipment slot, crafting remainder, the allow-enchanting event, custom damage
(through a player), a default component change and the creator mod; on 26.x also the item id.

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
| `EntityFluidInteractionRegistry` (26.x) | upstream's mixins, without three injectors | entering, leaving, pushing and moving through a Fabric fluid work; swimming, jumping out of it, drowning and boats floating in it don't (NeoForge rewrote that code around its fluid types) |
| Oxidation and wax scraping (26.3) | not applied | 26.3's block transformers have no NeoForge counterpart yet (a warning is logged once) |

On 26.3, composting, stripping and fuel are data-driven in vanilla, and Fabric API has no registries for them.

CI checks, on all four versions: a flammable block, a waxable pair and a custom game event's vibration frequency take
effect; up to 26.2 also a compostable item, a strippable block and a fuel value.

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
pipe reaches a NeoForge mod's block.

- Transactions are mirrored both ways: a simulation (an aborted transaction) on one side is aborted on the other, and
  nested transactions keep their meaning.
- Fluids convert at 81 droplets per millibucket. Only whole millibuckets move between the two: if a Fabric storage
  would take part of one, it is asked again for whole millibuckets.
- A NeoForge mod's own capability for its block wins over a Fabric storage, and for a NeoForge mod's block Fabric mods
  get its NeoForge handler before Fabric's generic `Container` wrapper, so the block's access rules hold.
- Vanilla blocks keep each side's own wrappers.
- Item storages inside items (`ItemStorage.ITEM`, `FluidStorage.ITEM`) and entity storages are not bridged yet.
- A Fabric storage that hands work to a NeoForge handler that in turn uses another Fabric storage, all in one
  transaction, is not supported (each side would wait for the other to close first).

On 1.21.11, `FluidVariantRendering`'s default sprites and colours come from NeoForge's fluid client extensions instead
of `fabric-rendering-fluids-v1`'s registry.

CI checks, on all four versions: a chest found through `ItemStorage.SIDED` keeps its items when a transaction is
aborted and changes when one commits; a full water cauldron empties when a bucket of water is extracted; a Fabric
storage used through NeoForge's capability and a NeoForge handler used through `ItemStorage.SIDED` follow aborts,
nested commits and commits; fluids move in whole millibuckets only.

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

### `fabric-rendering-v1` (26.x), `fabric-screen-api-v1` and `fabric-sound-api-v1`

Upstream's module loads, without the mixins that target code NeoForge rewrote:

- **HUD** (`HudElementRegistry`): NeoForge draws the HUD as layers, so upstream's HUD mixin can't apply.
  Fabricated wraps each of NeoForge's vanilla layers with the Fabric element list of the same vanilla element, so
  elements attached before or after a vanilla element, replaced or removed, behave as on Fabric. Its wrappers
  run after NeoForge mods registered their layers, so the Fabric elements surround whatever those mods did to the
  vanilla layers.
- **Picture-in-picture renderers** (`PictureInPictureRendererRegistry`): NeoForge rebuilt the GUI renderer around
  its own renderer pools; renderers registered with Fabric are not used yet.
- **Debug options screen**: keeps vanilla's sorting and search instead of upstream's namespace-aware ones.
- **Render pipeline snippets** (26.1.2, 26.2): NeoForge added a field to them, so they don't carry Fabric's
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
through `FluidRenderingRegistry.setBlockTransparency` keep NeoForge's behavior. Custom fluid renderers work.

`fabric-sound-api-v1` (all versions) loads without upstream's redirect of the audio stream lookup, which NeoForge replaced with
its own `SoundInstance.getStream`. A sound instance whose class overrides `FabricSoundInstance.getAudioStream`
gets its stream from it there; other sounds keep NeoForge's path.

### Fabric Loader

The public `net.fabricmc.loader.api` is complete: entrypoints (including custom ones and language adapters such as
Kotlin's), mod containers and metadata, versions and predicates, the object share, and the mapping resolver
(intermediary to Mojang names on 1.21.11).

- `@Environment` and `@EnvironmentInterface` are applied as Fabric Loader applies them: members and interfaces for
  the other side are stripped when the class loads, so a common class implementing a client-only interface loads on
  a dedicated server.
- Of Fabric Loader's internals, `FabricLoaderImpl.INSTANCE` (its public API methods) and
  `FabricLauncherBase.getLauncher()` (environment, development flag, runtime namespace) are provided for the mods that
  use them; other internal classes are not.
- Mods detect Fabricated with `FabricLoader.getInstance().isModLoaded("fabricated")`; see [API.md](API.md).
- Config screens a Fabric mod offers to Mod Menu (its `modmenu` entrypoint) open from the Config button of NeoForge's
  mod list. Mod Menu's API interfaces are provided, so such mods load without Mod Menu, which itself can't run on
  NeoForge (it would duplicate NeoForge's mod list).

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

Only Fabricated's own test mods are tested in CI so far. Results for real Fabric mods will be added here as
CI covers them. To report one, see [CONTRIBUTING.md](../CONTRIBUTING.md#reporting-a-compatibility-result).
