# Fabric API modules on Forge (experimental)

The `forge` and `forge-legacy` branches build Fabricated for Minecraft Forge 61 to 66 and Forge 47 (each branch's
`README.md`); this table compares both, and each branch holds a copy (`docs/FORGE-LEGACY.md` here, `docs/FORGE.md` on
`forge`). NeoForge's tables are in [`COMPATIBILITY.md`](COMPATIBILITY.md). CI runs dedicated servers there: `ci/forge/smoke-server.sh` and `ci/forge-legacy/smoke-server.sh`
start one with the test mod and one with the official Fabric API jar too, and `smoke-client.sh` starts a client the same
two ways (with Fabric API, informational). This table is the two runs with Fabric API, the server's and the client's
`fabric-bridge-report.txt`, from the runs on `claude/dev` at `6ee91f6` (40508578596, and 40508573809 for Forge 47),
2026-10-27: Fabric API 0.141.6+1.21.11, 0.155.3+26.1.2, 0.161.0+26.2, 0.164.0+26.3 and 0.92.12+1.20.1, on Forge 61.2.4,
64.1.5, 65.1.5, 66.1.0 and 47.4.28. Each cell is the more limited of the two: a module with client hooks
can load on the server and lose hooks, or be turned off, on the client. The server starts and the client reaches a world
in every run, and both server runs and the client run with the test mod are gates on every Forge version.

How to read it: as the NeoForge table, and

- **loads, part**: the module loads without some of its hooks, ones its plan leaves out where Forge's code differs or
  ones pre-flight finds Forge's code can't take (LEAVE-1). The report names each, with what is lost.
- **disabled**: the client run turns it off (the game goes on without it); the server loads it where it isn't
  client-only. No module is disabled on any Forge version: Forge 47's `fabric-particles-v1` was until PART-1, its
  accessor to the particle providers' map expecting vanilla's type, which Forge changed.

| Module | 1.21.11 (Forge 61) | 26.1.2 (Forge 64) | 26.2 (Forge 65) | 26.3 (Forge 66) | 1.20.1 (Forge 47) |
|---|---|---|---|---|---|
| fabric-advancement-api-v1 | — | — | loads | loads, part | — |
| fabric-api-base | native | native | native | native | native |
| fabric-api-lookup-api-v1 | loads | loads | loads | loads | loads |
| fabric-biome-api-v1 | loads | loads | loads | loads | native |
| fabric-block-api-v1 | loads | loads | loads | loads | loads |
| fabric-block-getter-api-v2 | — | loads | loads | loads | — |
| fabric-block-view-api-v2 | loads | — | — | — | loads |
| fabric-blockrenderlayer-v1 | — | — | — | — | loads |
| fabric-client-tags-api-v1 | — | — | — | — | loads |
| fabric-command-api-v1 | — | — | — | — | loads |
| fabric-command-api-v2 | loads | loads | loads | loads | loads |
| fabric-commands-v0 | — | — | — | — | loads |
| fabric-containers-v0 | — | — | — | — | loads |
| fabric-content-registries-v0 | loads | loads, part | loads, part | loads, part | loads, part |
| fabric-convention-tags-v1 | loads | — | — | — | loads |
| fabric-convention-tags-v2 | loads | loads | loads | loads | — |
| fabric-crash-report-info-v1 | loads | loads | loads | loads | loads |
| fabric-creative-tab-api-v1 | — | loads | loads | loads | — |
| fabric-data-attachment-api-v1 | loads | loads | loads | loads | loads |
| fabric-data-generation-api-v1 | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-debug-api-v1 | — | loads | loads | loads | — |
| fabric-dimensions-v1 | loads, part | loads, part | loads, part | loads, part | native |
| fabric-entity-events-v1 | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-events-interaction-v0 | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-events-lifecycle-v0 | — | — | — | — | loads |
| fabric-game-rule-api-v1 | loads | loads | loads | loads | loads |
| fabric-item-api-v1 | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-item-group-api-v1 | loads | — | — | — | loads |
| fabric-key-binding-api-v1 | loads | — | — | — | loads |
| fabric-key-mapping-api-v1 | — | loads | loads | loads | — |
| fabric-keybindings-v0 | — | — | — | — | loads |
| fabric-lifecycle-events-v1 | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-loot-api-v2 | loads | — | — | — | native |
| fabric-loot-api-v3 | native | native | native | native | — |
| fabric-loot-tables-v1 | — | — | — | — | native |
| fabric-menu-api-v1 | — | loads | loads | loads | — |
| fabric-message-api-v1 | loads | loads | loads | loads | loads |
| fabric-mining-level-api-v1 | — | — | — | — | loads |
| fabric-model-loading-api-v1 | loads | loads | loads | loads | loads |
| fabric-models-v0 | — | — | — | — | loads |
| fabric-networking-api-v1 | loads | loads | loads | loads | loads |
| fabric-networking-v0 | — | — | — | — | loads |
| fabric-object-builder-api-v1 | loads, part | loads, part | loads, part | loads, part | loads |
| fabric-particles-v1 | loads, part | loads | loads | loads | native |
| fabric-permission-api-v1 | — | loads | loads | loads | — |
| fabric-recipe-api-v1 | loads | loads | loads | loads | loads |
| fabric-registry-sync-v0 | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-renderer-api-v1 | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-renderer-indigo | loads, part | loads | loads | loads | loads, part |
| fabric-renderer-registries-v1 | — | — | — | — | loads |
| fabric-rendering-data-attachment-v1 | — | — | — | — | loads |
| fabric-rendering-fluids-v1 | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-rendering-v0 | — | — | — | — | loads |
| fabric-rendering-v1 | loads, part | loads | loads | loads | loads, part |
| fabric-resource-conditions-api-v1 | loads, part | loads | loads | loads, part | loads, part |
| fabric-resource-loader-v0 | loads | loads | loads | loads | loads |
| fabric-resource-loader-v1 | loads, part | loads, part | loads, part | loads, part | — |
| fabric-screen-api-v1 | loads, part | loads, part | loads, part | loads, part | loads |
| fabric-screen-handler-api-v1 | loads | — | — | — | loads |
| fabric-serialization-api-v1 | loads | loads | loads | loads | — |
| fabric-sound-api-v1 | loads | loads | loads | loads | loads, part |
| fabric-tag-api-v1 | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-transfer-api-v1 | loads | loads | loads | loads | loads |
| fabric-transitive-access-wideners-v1 | loads | loads | loads | loads | loads |

On top of the modules, the in-game mod bridges these to Forge, or makes Fabric API's hooks for them apply, each checked
by the server runs (the colours and screen input by the client runs):

- **Tags:** tag files' `fabric:remove` entries are read into Forge's own removal list, with and without Fabric API (all
  five).
- **Fluids:** Fabric mods' fluids get Forge fluid types, and an entity moves through one that isn't water as through
  air, as on Fabric (all five).
- **Default attributes:** `FabricDefaultAttributeRegistry.MODIFY` runs in Forge's attribute modification event, and a
  cow's armor set through it is what the game reads (Forge 64 to 66, with Fabric API).
- **Advancements:** a vanilla advancement's reward set through `AdvancementEvents.MODIFY` is what the server loads
  (Forge 65 and 66, with Fabric API; on Forge 66 through a hook on Forge's conditional decoding).
- **Colours:** Fabric's colour provider registry reads Forge's own map of block colours (Forge 61), and of block and
  item colours (Forge 47), by registry holder, where upstream's lookup reads the list by id Forge replaced (COLOR-1).
  It finds grass's provider and none for stone (Forge 61 and 47, with Fabric API). Forge 64 to 66 keep vanilla's list,
  which upstream's lookup reads as it is.
- **Networking:** a payload (Forge 61 to 66) or a channel's buffer (Forge 47) the client sends as it joins comes back
  from the integrated server with its answer, through Fabric API's module on Forge's network stack (all five, with
  Fabric API, client run).
- **Commands:** a command registered through `CommandRegistrationCallback` is in the server's dispatcher (all five, with
  Fabric API, server and client). Fabric API's hook into `Commands`' constructor applies on Forge 61 to 66 at a point
  pre-flight marks unsafe (CTOR-2), and on Forge 47, whose Mixin takes none, as a wrapper of the call (CTOR-4).
- **Block breaking:** `PlayerBlockBreakEvents` fire as a player breaks a block, and a `BEFORE` refusal keeps the block
  (all five, with Fabric API, server and client). Forge's `destroyBlock` makes the calls upstream's hooks look for in a
  `removeBlock` of its own, so the in-game mod fires the events there (BREAK-1).
- **Beds:** a block `EntitySleepEvents.ALLOW_BED` admits stays as it is while an entity sleeps on it, and the occupation
  and sleeping direction events run as it lies down and wakes up (all five, with Fabric API, server and client; on
  Forge 47 as it wakes up since LAMBDA-6). Forge sets a bed's occupation through its own `setBedOccupied`, on the red
  bed upstream's hook stands in for the block, so the in-game mod sets the block's own (SLEEP-1). On Forge 47 a player
  may sleep in the daytime `EntitySleepEvents.ALLOW_SLEEP_TIME` allows (with Fabric API, server and client): Forge 47
  checks its own sleep time event where upstream's hooks look for the day check, so the in-game mod answers it from
  Fabric's (SLEEP-2). Fabric API for 1.21.11 and later has no such event.
- **Damage:** an entity hurt through the game's own `hurtServer` asks `ServerLivingEntityEvents.ALLOW_DAMAGE`, whose
  refusal holds, and fires `AFTER_DAMAGE` with the damage it was dealt and the damage it took (Forge 61 to 66, with
  Fabric API, server and client; Fabric API 0.92.12 has no `AFTER_DAMAGE`). Forge 61's and 64's `hurtServer` drop the
  copy of the dealt damage upstream's hook reads, so the in-game mod fires the event there (DAMAGE-1).
- **Use and attack:** a player's uses of an item, a block and an entity, and its attacks on a block and an entity, ask
  `UseItemCallback`, `UseBlockCallback`, `UseEntityCallback`, `AttackBlockCallback` and `AttackEntityCallback` through
  Forge's own code, and a refusal holds (all five, with Fabric API, server and client). Upstream's hooks apply as they
  are, pre-flight renaming the captured value the entity hook shadows.
- **Screen input:** a click and a key press on a screen, and their releases, ask `ScreenMouseEvents` and
  `ScreenKeyboardEvents` (Forge 47, with Fabric API, client run). Forge 47's patch made the mouse lambdas take `this` and
  reordered the key lambda's captured values; pre-flight bridges upstream's hooks into both (LAMBDA-7).
- **Storages:** a Fabric mod's item and fluid storages are the `ITEM_HANDLER` and `FLUID_HANDLER` capabilities of its
  block entity, and a Forge mod's `ItemStackHandler` and `FluidTank` are Fabric's storages (all five, with Fabric API,
  server and client). A Forge handler only simulates until Fabric's outermost transaction commits, so an aborted one
  never reaches it, and two insertions in one transaction take no more than its slots hold; fluids move in whole
  millibuckets. On Forge 61 to 66 a fluid carrying data (Fabric's components, Forge's tag) doesn't cross. A Fabric mod's
  tank item is filled and drained through Forge's `FLUID_HANDLER_ITEM`, its stack changed in place when it stays the
  same item, and a Forge mod's through `FluidStorage.ITEM`, in Fabric's transaction. Item storages in items (a
  backpack's) don't cross yet.
- **Model baking:** a model loading plugin's before-bake and after-bake modifiers see the models baked (Forge 47, with
  Fabric API, client run). Forge 47 moved the bake's code into an overload that also takes the sprite getter, where
  pre-flight moves upstream's hooks, its before-bake variable modifier too (MODEL-3).

What else differs on Forge, for Fabric mods in general:

- **Loading errors:** when Forge refuses a mod file or a dependency is missing, it loads only Minecraft and Forge to show
  the error. Fabricated then leaves the game's classes alone, so Forge's own error is what the player sees (LOADERR-1).
  Forge itself refuses two Fabric jars in the corpus: JEI's on Forge 61 to 66 (its module-info) and YACL 3.6.6's on
  Forge 47 (its `mods.toml`). Their Forge builds work.
- **Jars built for Fabric and NeoForge at once** list their nested jars for NeoForge too. Fabricated hides that list
  from Forge's jar-in-jar locator and reads the jar itself, so each nested mod loads once, translated (JIJ-1).
- **MixinExtras** is 0.5.5 on every Forge version: the in-game mod nests it, and Forge's jar-in-jar locator picks it over
  the 0.5.4 Forge 61 to 66 offer (MIXEXT-3).
- **Forge Config API Port**'s Fabric build loads, with its own copies of NeoForge's config classes, which Forge lacks
  (REPL-1, PLAT-5). **Mod Menu**'s Fabric build isn't loaded on Forge, which lists mods and their config screens itself;
  its API isn't provided there.
- **Enum extensions:** a Fabric mod adding enum constants with Mixin's enum extensions (Farmer's Delight Refabricated)
  is disabled on Forge 61 to 66, whose Mixin 0.8.7 predates them (CORPUS-36).
- **The corpus runs** reach the game on every Forge version since LOADERR-1: 85 to 104 Fabric mods load on the Forge 64
  to 66 servers and 135 on Forge 47's, 104 to 151 on the clients (runs 40570195894 and 40570200260). They stop later,
  on what *Findings* in [roadmap.md](roadmap.md#findings) lists (PRE-18, CORPUS-33).

