# Fabric API modules on Forge

The `forge` and `forge-legacy` branches build Fabricated for Minecraft Forge 61 to 66 and Forge 47 (each branch's
`README.md`). From 0.6.0-beta Forge is held to NeoForge's bar: no launch stop caused by Fabricated and no open P0 or P1
finding (the findings still open are in [roadmap.md](roadmap.md#findings)). Coverage differs, and what Forge lacks is
listed below under *Unsupported on Forge*, with the task that takes it on where one does. This page compares both
branches, and each holds a copy (`docs/FORGE.md` on `forge`, `docs/FORGE-LEGACY.md` on `forge-legacy`). NeoForge's
tables are in [`COMPATIBILITY.md`](COMPATIBILITY.md).

CI runs dedicated servers there: `ci/forge/smoke-server.sh` and `ci/forge-legacy/smoke-server.sh` start one with the
test mod and one with the official Fabric API jar too, and `smoke-client.sh` starts a client the same two ways. Every
run is a gate on every Forge version. This table is the two runs with Fabric API, the server's and the client's
`fabric-bridge-report.txt`, from task 3's pull requests on 2026-11-02 (runs 41382942938 for Forge 61 to 66 and
41385466464 for Forge 47): Fabric API 0.141.6+1.21.11, 0.155.3+26.1.2, 0.161.0+26.2, 0.164.0+26.3 and 0.92.12+1.20.1,
on Forge 61.2.4, 64.1.5, 65.1.5, 66.1.0 and 47.4.28. Each cell is the more limited of the two: a module with client
hooks can load on the server and lose hooks, or be turned off, on the client.

How to read it: as the NeoForge table, and

- **native**: Fabricated ships the module itself, on Forge's events; the official jar's copy isn't loaded.
- **loads, part**: the module loads without some of its hooks, ones its plan leaves out where Forge's code differs or
  ones pre-flight finds Forge's code can't take (LEAVE-1). The report names each; where Fabricated does the hook's
  work another way, the bullets below say so, and what is lost is under *Unsupported on Forge*.
- **disabled**: the client run turns it off (the game goes on without it); the server loads it where it isn't
  client-only. No module is disabled on any Forge version.

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
| fabric-key-binding-api-v1 | native | — | — | — | native |
| fabric-key-mapping-api-v1 | — | native | native | native | — |
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
| fabric-resource-conditions-api-v1 | loads, part | loads | loads | loads | loads, part |
| fabric-resource-loader-v0 | loads | loads | loads | loads | loads, part |
| fabric-resource-loader-v1 | loads, part | loads, part | loads, part | loads, part | — |
| fabric-screen-api-v1 | loads, part | loads, part | loads, part | loads, part | loads |
| fabric-screen-handler-api-v1 | loads | — | — | — | loads |
| fabric-serialization-api-v1 | loads | loads | loads | loads | — |
| fabric-sound-api-v1 | loads | loads | loads | loads | loads, part |
| fabric-tag-api-v1 | loads, part | loads, part | loads, part | loads, part | loads, part |
| fabric-transfer-api-v1 | loads | loads | loads | loads | loads |
| fabric-transitive-access-wideners-v1 | loads | loads | loads | loads | loads |


On top of the modules, the in-game mod bridges these to Forge, or makes Fabric API's hooks for them apply, each checked
by the server runs (the colours, screens, key mappings and config screens by the client runs):

- **Tags:** tag files' `fabric:remove` entries are read into Forge's own removal list, with and without Fabric API (all
  five).
- **Fluids:** Fabric mods' fluids get Forge fluid types, and an entity moves through one that isn't water as through
  air, as on Fabric (all five).
- **Registry data:** a Fabric mod's own pack takes Fabric API's known-pack identity, so its registry entries (worldgen,
  damage types) are stable, as on Fabric, and a world holding them opens without asking to confirm experimental
  settings (Forge 61 to 66, server and client; PACK-5). Forge gives mods' packs none, and Forge mods' packs keep that.
- **Built-in packs:** a built-in pack a Fabric mod's client entrypoint registers as enabled by default is switched on
  the first time the game offers it (all five, with Fabric API, client run). Fabric API keeps its file of the packs it
  offered, so a pack the player turns off stays off, as on Fabric. Upstream does this as the game's options load,
  which on Fabric is after the Fabric mods' entrypoints and on Forge before them. So the plans leave that hook out,
  and the in-game mod does its work once the entrypoints have run, before the game selects its packs from the
  options (CORPUS-104).
- **Load conditions:** of two advancements (a JSON data loader's files) and two damage types (a data pack registry's
  entries) with `fabric:load_conditions`, the server loads the ones whose conditions hold and not the others (Forge 61
  to 66, with Fabric API). Forge 61 decodes registry entries through its own conditional decoder, which the in-game mod
  hands Fabric's conditions to, and Forge 61's and 66's JSON data loaders hand the data to lambdas taking more values
  than vanilla's, which pre-flight bridges upstream's hook into (LAMBDA-8). On Forge 47, a recipe's or an
  advancement's own `conditions`, which Forge reads first, take a type Forge has no serializer of from Fabric API's
  resource conditions, tested on the condition's JSON as Moonlight's bridge tests it on Fabric: Supplementaries' recipes
  name their own condition there (CORPUS-97). Of the test mod's two recipes naming its Fabric condition, the server
  loads the one whose condition holds, and neither fails to parse (with Fabric API).
- **Biomes:** a feature `BiomeModifications.addFeature` adds to the plains is among their features (Forge 61 to 66, with
  Fabric API). Upstream rebuilds each generator's feature list, a field Forge retypes, which the in-game mod writes with
  Forge's type (CORPUS-33). On Forge 47 the module is native: Fabric's changes go through Forge's biome modifiers, and a
  spawn and a temperature added to the plains reach the biome Forge reads.
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
  pre-flight marks unsafe (CTOR-2), and on Forge 47, whose Mixin takes none, at Fabricated's own injection point, which
  finds what the hook's point finds and which Forge 47's Mixin takes anywhere after the constructor's `super()` call
  (CORPUS-103, which replaced CTOR-4's wrapper of the call). So does a hook shifted past a construction there, as Dripstone
  Fluid Lib's into `Minecraft`'s constructor is (the test mod's in `Commands`' sees the dispatcher just stored).
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
- **Effects:** an effect's events learn the command that gave it, and an effect given by code comes from none (Forge
  61 to 66, with Fabric API, server and client; Fabric API 0.92.12 has no effect events). Upstream's hook is in
  Brigadier, a library Forge loads without mixins, so the in-game mod sets the same context where vanilla's command
  execution runs each command, a player's, the console's, a function's or a command block's (EFFECT-1). A command a mod
  runs through Brigadier's own dispatcher gives none.
- **Use and attack:** a player's uses of an item, a block and an entity, and its attacks on a block and an entity, ask
  `UseItemCallback`, `UseBlockCallback`, `UseEntityCallback`, `AttackBlockCallback` and `AttackEntityCallback` through
  Forge's own code, and a refusal holds (all five, with Fabric API, server and client). Upstream's hooks apply as they
  are, pre-flight renaming the captured value the entity hook shadows.
- **Screens:** a screen's initialization and drawing events fire, and a key press, a key release, a character typed, a
  mouse click and its release on a screen ask `ScreenKeyboardEvents` and `ScreenMouseEvents`, whose refusals hold (Forge
  64 to 66, with Fabric API, client run). Forge calls a screen's input handlers and draws it through hooks of its own,
  where upstream's wrappers look for vanilla's calls, so the in-game mod wraps the same calls inside Forge's hooks and
  fires the drawing events from Forge's `ScreenEvent.Render` (SCREEN-1). Forge 61 has the same bridges, which no check
  runs yet: 1.21.11 has no client test mod for Fabric API's client modules. On Forge 47 a click and a key press, and
  their releases, ask the same events (with Fabric API, client run): its patch made the mouse lambdas take `this` and
  reordered the key lambda's captured values, and pre-flight bridges upstream's hooks into both (LAMBDA-7).
- **HUD:** a Fabric HUD element attached after chat and one attached before the hotbar are drawn (Forge 61 to 66, with
  Fabric API, client run). Forge 61 to 66 draw the HUD as layers of their own, where upstream's hooks into vanilla's
  HUD method no longer run, so the in-game mod wraps each of Forge's layers with the Fabric elements attached to,
  replacing or removing the same vanilla element, vanilla's conditions around it checked first (HUD-2). Removing
  Fabric's `info_bar` element also keeps the bar from switching between the experience and the locator bar on Forge 61
  to 66, whose layer does both. A Fabric mod's own hooks into the HUD method run where Forge's HUD does its work
  (HUD-4; the test mod's, all five, client run). Forge 61 to 66's patch draws Forge's layers at the method's start and
  returns before vanilla's code, so pre-flight points a hook at its end (malilib's, which MiniHUD, Tweakeroo and
  Litematica draw from, on Forge 61 and 64) at its returns, and one at a call drawing a part of the HUD, which Forge's
  layer makes itself, at the start of the called method. Forge 47's GUI overrides the method and never calls it, so
  pre-flight points hooks at its start and end at Fabricated's seams, which run where Forge's GUI starts and ends its
  HUD drawing, a hook cancelling at the start skipping it. Fabric API's own hook fires the deprecated
  `HudRenderCallback` (1.20.1's HUD API on Forge 47) so, after Forge's last layer, outside the loading screen (Forge 61
  and 47, with Fabric API, client run).
- **Key mappings:** on all five Forge versions the key mapping module (the key binding module on 1.21.11 and 1.20.1) is
  native, with Fabric API's jar and without it (client runs). Upstream refuses a key mapping once the game's options
  exist, which Forge creates before it loads mods, so passed through, it failed every client entrypoint that registers
  one (KEYMAP-3; on Forge 47 found with Xaero's World Map). Fabricated keeps them until Forge's
  `RegisterKeyMappingsEvent`, where Forge mods register theirs; Forge then reads the saved keys again. A test key
  mapping is in the options, sorted after the game's, and bound to the key CI's options file saves for it; a second
  registration of it, and one after Forge's event, are refused, as upstream refuses them.
- **Config screens:** a Fabric mod's Mod Menu config screen is behind Forge's mod list button, as on NeoForge (all five,
  client run).
- **Failed initializers:** a Fabric mod whose entrypoint throws is named in the report and in Forge's loading warnings,
  and what it built but never registered is dropped at the registries' freeze with an error, where it stopped the
  freeze (all five, server run; HOLD-2). Fabricated's API runs what waits for the initializers once they ran
  (`Fabricated.get().afterEntrypoints`).
- **Registration order:** a Fabric mod may make its objects first and register them after, in any order, as Fabric
  lets it: a bucket made before its fluid is registered holds that fluid (all five), and on Forge 47 so do a liquid
  block made before its fluid and a music disc made before its sound, and a block item registered before its block is
  that block's item (server run; CORPUS-73). Forge's patches of those constructors, and Forge 47's of a block item's
  block, looked the object up in its registry and threw: TechReborn's fluids didn't load (seen on Forge 47, 64 and 65)
  nor Lootr's block items on Forge 47, and what they left registered stopped Forge's snapshot of the registries.
- **Registry aliases:** an old name a Fabric mod keeps for a renamed entry with Fabric API's `FabricRegistry.addAlias`
  names that entry in the registries Forge manages, in their lookups by name and by key (which a data pack's references
  go through) and in Forge's own registry, its snapshots and its sync to clients (all five, with Fabric API; server
  run; CORPUS-75). Fabric API kept the alias in vanilla's registry class, whose lookups Forge's wrapper replaces:
  Waystones' world generation names its renamed waystones by their old names, and its data pack, and with it the
  server, didn't load on Forge 64 and 65.
- **Registry entry added events:** Fabric API's `RegistryEntryAddedCallback` fires for the registries Forge manages,
  once an entry new to one is in it, with its raw id, whether a Fabric mod registered it through vanilla's API or a Forge
  mod through Forge's (all five, with Fabric API; server run; CORPUS-108). Upstream fires it from vanilla's registry
  method, which Forge's wrapper of those registries replaces, so it never fired for blocks, items, entity types and the
  rest. Architectury gives a spawn egg its entity type from a listener on that event: Artifacts' egg kept none, and EMI's,
  JEI's and REI's listings of its creative tab failed (EMI's own plugin with them). Where Forgified Fabric API provides
  the module, its own copy fires the event.
- **Recipe slots on the network (Forge 61 to 66):** an empty optional ingredient, a shaped recipe's empty slot, is sent
  as vanilla sends it, an empty item list, and read back as empty (server run; CORPUS-83). Forge's patch of vanilla's
  codec threw writing it, so with Fabric API, whose recipe sync sends the recipes Fabric mods' clients need, a shaped
  recipe with an empty slot dropped the player's connection to the world they joined (seen on the Forge 65 corpus
  client). A Fabric mod's own ingredient, a subclass of vanilla's without
  Forge's `serializer()` (Fabric API's custom ingredients are one), goes in vanilla's form after a marker of its own,
  through the codec Fabric API wraps with its custom form, so one of Fabric API's goes as Fabric API sends it (server
  run, with Fabric API for its custom ingredients; CORPUS-85). Forge's codec threw writing it, and the Forge 65 and 66
  corpus clients waited for their player until the run's timeout. In a recipe slot, Forge's custom ingredients are
  read by Forge's codec, as Fabric API's wrapper took the -1 they start with for its own. Only the network form
  changes: a Fabric mod's subclass other than Fabric API's still can't be written as JSON (no mod seen doing it).
- **Custom ingredients and crafting size (Forge 47):** a recipe ingredient with Fabric API's `fabric:type` is that
  custom ingredient, as Forge reads every ingredient itself before Fabric API's hook can; inside an array it joins
  Forge's compound ingredient, as Forge's own custom ingredients do (CORPUS-99). On the network, Forge's custom
  ingredients, whose -1 marker Fabric API's reader took for its own, are read by Forge (NET-11). A size given to Porting
  Lib's `ShapedRecipeUtil.setCraftingSize` is given to Forge's too, so Create's 5 by 5 mechanical crafting recipes
  load. The test mod reads a `fabric:any` alone and in an array as a recipe reads an ingredient, and both come back from
  the network as they went (with Fabric API, server run).
- **Drop capture (Forge 47):** a dying entity's drops stay in Forge's capture after Forge's `LivingDropsEvent` (none
  stay where a listener cancelled it) until every mod's hook at the return of `dropAllDeathLoot` has run, and
  Fabricated adds what is still captured to the level after them, as Forge's method did. Porting Lib ports that
  capture to Fabric under Forge's names, so on Forge its hook there takes them, fires its own drops event and adds them;
  Forge's method used to take them first, and Porting Lib's hook threw as any mob died (CORPUS-105). The test mod's own
  hook there stands in for Porting Lib's: a pig it kills at the first tick leaves it its drops, which reach the level
  (server run).
- **Storages:** a Fabric mod's item and fluid storages are the `ITEM_HANDLER` and `FLUID_HANDLER` capabilities of its
  block entity, and a Forge mod's `ItemStackHandler` and `FluidTank` are Fabric's storages (all five, with Fabric API,
  server and client). A Forge handler only simulates until Fabric's outermost transaction commits, so an aborted one
  never reaches it, and two insertions in one transaction take no more than its slots hold; fluids move in whole
  millibuckets. On Forge 61 to 66 a fluid carrying data (Fabric's components, Forge's tag) doesn't cross. A Fabric mod's
  tank item is filled and drained through Forge's `FLUID_HANDLER_ITEM`, its stack changed in place when it stays the
  same item, and a Forge mod's through `FluidStorage.ITEM`, in Fabric's transaction. Items in items cross the same way
  on Forge 61 to 66: a Fabric mod's item storage in an item (a bag's) is its stack's `ITEM_HANDLER`, the stack changed
  in place, and a Forge mod's item handler on a stack is Fabric's `ItemStorage.ITEM`; a move that would leave another
  item in the bag's place, which Forge's handler of a stack can't hand back, doesn't happen. Fabric API for 1.20.1 has
  no lookup for item storages in items, so Forge 47 has none to cross.
- **Fuels and brewing (Forge 47):** an item `FuelRegistry` makes a fuel is one to Forge's furnace, and an awkward potion
  brewed with an item a `FabricBrewingRecipeRegistry` recipe names gives the recipe's potion (with Fabric API, server
  and client). Forge's furnace reads its burn times from vanilla's fuel table as tags load, where upstream's hook adding
  Fabric's fuels is left out with the rest of its furnace mixin, so the in-game mod adds them (FUEL-1). Upstream builds
  a brewing recipe with vanilla's constructor of a brewing mix, which Forge replaced, so every call threw and failed
  the initializer making it; the in-game mod builds it with Forge's (BREW-1). The report still lists that call among
  its linkage warnings.
- **Model baking:** a model loading plugin's before-bake and after-bake modifiers see the models baked (Forge 47, with
  Fabric API, client run). Forge 47 moved the bake's code into an overload that also takes the sprite getter, where
  pre-flight moves upstream's hooks, its before-bake variable modifier too (MODEL-3).
- **Model loaders (Forge 47):** Forge reads every block model's `loader` and fails the model when its own registry
  lacks that loader, where vanilla ignores the key and a Fabric mod reads its own loaders through a hook into vanilla's
  model reader (Moonlight's for Supplementaries, Porting Lib's for `porting_lib:obj` and the rest). A loader in a Fabric
  mod's namespace, or in no loaded mod's, is left to those hooks, as on Fabric; one in Forge's namespace or a Forge
  mod's fails as without Fabricated (CORPUS-92, client run).
- **Language files on the server (Forge 47):** Forge's server reads every mod's language file with its own copy of
  vanilla's reader, which no Fabric mod hooks. The in-game mod has it use vanilla's reader, as a Fabric server does
  through Fabric API, so a Fabric mod's hook applies there: owo-lib's lets its files hold rich text, which Forge's copy
  refused (CORPUS-100, server run).
- **Fabric's renderer API (task 4, R5):** a Fabric block model (one with its own emitter) is drawn in chunk sections,
  alone and inside a vanilla multipart model, as a falling block and with the breaking overlay, on Forge 61 to 66 as a
  minecart's block too and on Forge 47 as a block a piston moves, and a Fabric item model's quads in the hotbar, the
  hand, an item frame and on the ground (all five, with Fabric API, client run). On Forge 61 to 66 upstream's hooks send
  every block through Fabric's renderer, as on Fabric. Forge 61 moved the code three of them hook into, so pre-flight
  leaves out those for moving blocks, the breaking overlay and blocks drawn as entities, and the in-game mod sends a
  block whose model is a Fabric model, or a multipart or weighted model with one among its parts, to Fabric's renderer
  there instead; every other block keeps Forge's path. Forge 61 to 66 also ask a block model for its particle sprite
  with Forge's model data where upstream's hooks ask Fabric's level-aware one, in places those hooks no longer reach:
  the screen effect inside a block, a block marker (Forge 64 to 66) and a block particle, whose sprite Forge sets again
  from the block's position. There the in-game mod asks Fabric's for such a model, and a model whose class gives its
  own sprite for model data keeps Forge's (PART-4; the test block's model, Forge 61 to 66). On Forge 47 upstream's hooks miss Forge's chunk section compiler
  and block renderers, which call Forge's overloads taking model data and a render type: pre-flight leaves out the
  chunk section redirect and keeps the hook that prepares Indigo's terrain context, and the in-game mod has Indigo draw
  such a block, once (at its first render type), in chunk sections, as a falling block, as a block a piston moves, with
  the breaking overlay and wherever the block renderer draws one block. Items keep Indigo's own hook there, and blocks
  drawn as entities their vanilla quads, as upstream draws them on 1.20.1. FRAPI rule 8: where Fabric's renderer
  (Indigo) calls a block model's emitter, a Fabric model that throws fails alone. The block is drawn from the model's
  parts (on Forge 47 by Forge's renderer, from the quads the model gives it), the failure is logged once and listed in
  the report, and the model's class is drawn that way from then on, but not the class of a model wrapping it (as
  Continuity's wrap others). A transform it left pushed is popped, so it never reaches another block. The renderer test
  mod checks each of these with a model that throws in a chunk section, as a multipart model's part, inside a model
  wrapping it, and on Forge 61 to 66 as a minecart's block. The outline Fabric's renderer draws around a glowing falling
  block is checked on Forge 65; on Forge 66 it isn't seen yet. Fabricated's guards apply only with Fabric's renderer API
  installed (they name its types), and the two that sit in methods upstream replaces (blocks drawn as entities, and the
  breaking overlay on Forge 64) apply after upstream's replacement.

What else differs on Forge, for Fabric mods in general:

- **Covered by Forge itself:** upstream's fix for emerald trades with other mods' villager types, as Forge 61 to 66
  skip the same check and give no trade for a type without one, upstream's DataFixerUpper hooks for a dimension whose
  chunk generator type no mod registers, as Forge's patch to the same schema keeps the generator's data (all five), and
  upstream's fix for the breaking overlay's flicker on renderer API models (`OverlayVertexConsumer`), as Forge 47 picks
  the overlay's direction the same stable way (its `stabilizeDirectionGetNearest` option, on by default). The report
  lists those hooks as left out or skipped.
- **Loading errors:** when Forge refuses a mod file or a dependency is missing, it loads only Minecraft and Forge to
  show the error. Fabricated then leaves the game's classes alone, so Forge's own error is what the player sees
  (LOADERR-1). Forge itself refuses two Fabric jars in the corpus: JEI's on Forge 61 to 66 (its module-info) and YACL
  3.6.6's on Forge 47 (its `mods.toml`). Their Forge builds work.
- **Jars built for Fabric and NeoForge at once** list their nested jars for NeoForge too. Fabricated hides that list
  from Forge's jar-in-jar locator and reads the jar itself, so each nested mod loads once, translated (JIJ-1).
- **Other copies of Fabric's parts:** a Fabric API module another Forge mod provides (one bundling Fabric API's modules)
  is that mod's, and Fabricated's copy, its mixins and its Forge hooks stand down, as on NeoForge (all five, server run
  with stand-ins; PROVIDER-1). Sinytra Connector and a second Fabricated jar are refused, the launch stopping with
  Fabricated's reason (DUP-1), and so, at once, is a Forge jar with its own copy of Fabric Loader, beside which Forge
  can't even build its error screen (all five; PROVIDER-2). Not Forgified Fabric API's on Forge 47 (next item).
- **Forgified Fabric API (Forge 47):** its 1.20.1 build nests Forgified Fabric Loader, whose packages are Fabric
  Loader's API, as Fabricated's own copy's are. Fabricated's in-game mod nests an empty jar under that loader's
  jar-in-jar identifier (`dev.su5ed.sinytra:fabric-loader`) at version 999.999.999, as Sinytra Connector does: Forge's
  jar-in-jar adds the newest version every range admits, so it adds the empty jar and leaves the copy out, and the mod
  locator lets the jar load (PROVIDER-2). Its modules are Forge mods whose `@Mod` classes start them and which use
  Fabric Loader's public API only (read off its jar), so Fabricated's copy serves them. Fabricated's own Fabric API
  modules stand down for theirs, with the parts of the in-game mod serving those modules and its transfer bridge
  (PROVIDER-1, PROVIDER-13, PROVIDER-16). The in-game mod is then loaded even without Fabric mods, as only it nests the
  empty jar. A copy under another identifier, in a jar's own entries, or with a range the empty jar's version doesn't
  satisfy is still refused. A provider's module is used as it is, and what it does differently from upstream is its
  own. Read in Forgified Fabric API's source (0.92.6): a mod's own loot table reports its source as vanilla, as Forge
  builds every mod's pack as built in (PROVIDER-14); a key binding registered once the game's options exist isn't
  refused, and on Forge they exist before mods load; its fake player is Forge's, whose connection drops every packet, so
  a fake player using an entity does nothing; a Fabric fuel's burn time is answered through Forge's burn time event,
  not vanilla's fuel table; a built-in pack a Fabric mod enables by default is offered but not switched on, as it
  switches such packs on as the options load, before Fabric mods register theirs; and its transfer bridge reports the
  last part of a millibucket filled as 0 mB, gives Fabric mods' items no fluid handler, never hands the context the
  item a Forge item's handler turns into, and lets a second insertion into a Forge item handler in one transaction
  miss the first, each simulated on the handler as it stands (PROVIDER-15). A stand-in nesting a copy as Forgified
  Fabric API does is a gate, and so is the server with Forgified Fabric API itself, its newest build, since it passed
  two extended runs in a row (CI-1); the client with it is informational, in extended runs. The test mod takes those
  answers as the provider's there and checks the rest.
- **Default method bridges:** where an interface a Fabric mod's mixin adds to a game class brings a second default
  method beside one Forge's own interfaces give it (`IForgeLivingEntity.self()` on `LivingEntity`, `IForgeEntity`'s
  `isMultipartEntity()` on every entity class), the class declares the method, calling Forge's, as on NeoForge
  (CORPUS-11, CORPUS-63, DEFAULT-1); without it either call threw `AbstractMethodError`. Forge runs on ModLauncher, so
  the loader's transformer declares them before Mixin, and a Mixin extension of the in-game mod takes them out as Mixin
  starts on a class it applies mixins to and declares them again after. The test mod calls both kinds on a pig (all
  five, server run).
- **Before the entrypoints:** Fabric mods' handlers in a registry's freeze, and a hook of theirs into the game's options
  that throws, wait for their main entrypoints as on Fabric (REG-8, ENTRY-2; Forge's client creates the options before
  it loads mods), and the system property FML sets for an empty log marker is removed before any Fabric mod runs
  (CORPUS-60). A Fabric mod's jar a mod proposes for the class path as the game runs isn't added, with a loading
  warning (DOWNLOAD-1); Essential, whose loader does that, loads from its download in the next launch, as on NeoForge.
- **The difficult mods' Fabric builds** (task 5): each loads by default and the client reaches a world with every
  smoke check passing, a gate in extended runs: malilib with MiniHUD, Tweakeroo and Litematica; Sodium's, the renderer
  test mod's checks passing under it (with Indium on Forge 47), and Iris's with it (IRIS-1's repairs on Forge 47); both
  of Essential's launches; and Flashback's on Forge 61 to 66 (PLUGIN-1's repair on 64 to 66; Modrinth has no build for
  1.20.1). The compatibility plan refuses none of them on Forge.
- **Forge's coremods:** pre-flight reads the game's classes as Forge's own transformers leave them before Mixin
  applies any mod's mixins: Forge 47's coremods and Forge 61 to 66's `forge-transformers`. Each mob's `finalizeSpawn`
  call Forge changes is pointed at `ForgeEventFactory.onFinalizeSpawn` (on Forge 47 anywhere in the classes it lists;
  on Forge 61 to 66 in the methods it names and those classes' lambdas), and the fields Forge reads through getters are
  read so, so a Fabric mod's hook at such a call moves to Forge's hook or is left out with its reason, as on NeoForge
  (CORPUS-61; on Forge 61 to 66 CORPUS-78: Friends and Foes' patrol spawner hook stopped the Forge 64 and 65 servers).
- **MixinExtras** is 0.5.5 on every Forge version: the in-game mod nests it, and Forge's jar-in-jar locator picks it
  over the 0.5.4 Forge 61 to 66 offer (MIXEXT-3). Its mod's root is the in-game mod's files, which owo reads, as
  it reads every mod's (CORPUS-77: owo's client stopped on Forge 47).
- **`@ModifyArgs`:** Forge 47's Mixin 0.8.5 puts the classes it generates for a `@ModifyArgs` handler's arguments in no
  module of the game layer, so a game class naming one failed to load (the corpus server and client stopped as `Fluids`
  loaded, CORPUS-68). Fabricated's transformation service adds a module holding those packages, and no files, as
  Mixin 0.8.7 does on Forge 61 to 66; not when no Fabric mod is loaded, or a mod file already holds one of them.
- **A shadow of another mixin's field:** Forge's Mixin (SpongePowered's 0.8.5 and 0.8.7) finds a `@Shadow` field only
  among its class's own, where Fabric's also finds those the mixins it prepared before add: MoreCulling's shadow of
  the mesh Fabric API's renderer mixin adds to `BlockModelRenderState` stopped the Forge 26.x clients. Such a shadow is
  declared as the other mixin declares the field, and Mixin merges the two into one field, as on Fabric (CORPUS-74:
  the test mod's tick counter, all five, server run); one that can't be (a static shadow of an instance field, a
  writable one of a final field) disables its mod, or leaves its mixin out where it can.
- **Two mixins' unique fields of one name:** for the same reason, Forge's Mixin renames a private `@Unique` field only
  when its class declares one of that name, not when another mixin adds it, and two mixins' fields became one, where
  Fabric's Mixin keeps a field each: Fabric API's screen mixin and SpruceUI's both keep `tickingScreen` in `Minecraft`,
  and the Forge 64 corpus client stopped as it created its world (CORPUS-95). Pre-flight renames a Fabric mod's such
  field, below public, when another mixin adds one of its name and type (the test mod's two counters, all five, server
  run).
- **Locals where Mixin miscounts them:** Forge 61 to 66's Mixin 0.8.7 works out the locals at an injection point from
  the frames it recorded from the class's bytes, which Forge's ModLauncher hands it with their frames compressed
  (Forge 47's Mixin 0.8.5 reads a class's frames expanded): each frame then states only its change, and Mixin can count
  fewer locals than are live, so a local capture, `@ModifyVariable` or MixinExtras' `@Local` taking one beyond finds
  none. Resourceful Lib's `@Local PoseStack` in `ScreenEffectRenderer.renderScreenEffect` stopped the Forge 61 and 64
  corpus clients (CORPUS-79). There, before mixins apply to a class a Fabric mod's mixin targets, a Mixin extension of
  the in-game mod finds each method whose locals Mixin would count wrong at some frame and records its frames again
  from the method with its frames expanded, which Mixin counts as with its `CLASSREADER_EXPAND_FRAMES` option, and logs
  the method. Every other method, and every class no Fabric mod's mixin targets, stays as Mixin recorded it. The test
  mod takes a local so (all five, server run; Forge 47's Mixin counts it right without the repair).
- **Fields Forge 47 retyped or replaced:** a Fabric mod's accessor of a field Forge 47 gave another type (a brewing
  mix's `from` and `to`, holders there; the particle engine's providers, kept by id there) or replaced (a mob bucket's
  entity type, a supplier there) invokes a getter the in-game mod adds, which gives vanilla's type: the holder's value,
  a view by raw id of Forge's map, and the bucket's `getFishType` (CORPUS-37: Porting Lib's accessors, and with them its
  extensions and Farmer's Delight's entrypoint, failed pre-flight; CORPUS-94: Supplementaries' bucket accessor stopped
  the corpus server's data reload). A mod's own read of such a field by vanilla's type calls the getter too
  (CORPUS-107: REI's default client plugin reads a mix's `from` itself, and failed before its later displays). The test
  mod reads a mix's potions and the pufferfish bucket's type so on the server, the mix's `from` by the field itself
  too, and the providers on the client. An accessor or read of another such field still fails.
- **Forge Config API Port** on Forge 61 to 66: its Fabric build loads, with its own copies of NeoForge's config
  classes, which Forge lacks (REPL-1, PLAT-5). On Forge 47 its version for 1.20.1 copies Forge's own config system,
  packages included, so its copies were left out for Forge's and its registry failed to link (Ponder's main
  entrypoint, CORPUS-81). There the jar is left out, as on NeoForge, and the in-game mod provides the port's Fabric API
  (`ForgeConfigRegistry`, `ForgeConfigPaths`, `ModConfigEvents`) on Forge's config system: each config is made with
  the Fabric mod's Forge container under the port's file name, its values readable right after registering, as with
  the port, and Forge loads, watches, syncs and unloads it as its mods' own, the port's events firing from Forge's.
  The test mod registers a config so and checks it each server run (PLAT-6: the descriptor had NeoForge's list until
  task 5). **Mod Menu**'s Fabric build isn't loaded on Forge, which lists mods itself; Fabric mods' Mod Menu
  entrypoints give it their config screens (above).
- **Enum extensions:** a Fabric mod adding enum constants with Mixin's enum extensions (Farmer's Delight Refabricated)
  is disabled on Forge 61 to 66, whose Mixin 0.8.7 predates them (CORPUS-36).
- **The corpus runs** (informational; extended runs 41385577023 and 41386136422 on task 3's code) load 87 to 106
  Fabric mods on the Forge 61 to 66 servers and 135 on Forge 47's, 105 to 124 on the Forge 61 to 66 clients and 151 on
  Forge 47's, then stop before a world, on what *Findings* in [roadmap.md](roadmap.md#findings) lists for task 5: the
  Forge 61 to 66 clients as the game bootstraps and the Forge 64 to 66 servers as Mixin starts (CORPUS-53), the Forge
  61 and Forge 47 servers on a corpus mod's hook into `CropBlock.getGrowthSpeed` that pre-flight let through
  (CORPUS-54), and Forge 47's client on a method the game's class loader can't find (CORPUS-55). The test mods' runs,
  which are the gates, have none of those mods. Lithostitched and Terralith, which pre-flight disabled until CORPUS-33,
  load on Forge 61 to 66.

## Unsupported on Forge

What Fabric mods can't rely on there. The report names every hook left out; these are the ones nothing on Forge does
instead. Unless a task is named, nothing is planned for them yet.

**Not supported on Forge** (Forge's code differs where upstream hooks in):

- **Item API.** A Fabric item's stack-aware recipe remainder (`FabricItem.getRecipeRemainder`) in furnaces, brewing
  stands and crafting, where Forge's own per-stack remainder hook applies (all five). On Forge 61 to 66,
  `FabricItem.canBeEnchantedWith` at the enchanting table, where Forge asks its own hook, and
  `allowContinuingBlockBreaking` as the stack changes while mining (client). On Forge 66, upstream's leave for a Fabric
  mod's fishing rod to keep its hook out, where Forge checks its own fishing action. On Forge 47, a Fabric item's
  per-stack attribute modifiers and `isSuitableFor`, where Forge's own per-stack hooks apply.
- **Content registries.** On Forge 64 to 66, an `EntityFluidInteractionRegistry` behaviour: entities swim, travel,
  sprint and float a boat in a fluid as its Forge fluid type lets them, which for a Fabric fluid is Fabricated's,
  treating it as water when it is tagged `minecraft:water` and as air otherwise.
- **Lifecycle events.** `BLOCK_ENTITY_UNLOAD` doesn't fire for a block entity a chunk drops as it reads it again (Forge
  61 to 66, server; Forge 47, client); the other block entity and chunk events fire.
- **Object builder.** A Fabric wood type's sign and hanging sign materials (Forge 61 and 64) and its hanging sign edit
  screen's texture (Forge 61 to 66).
- **Registry sync.** Data generation of other mods' dynamic registries (Forge 61 to 66). Forge syncs registry ids
  itself, and Fabricated runs the built-in registries' freeze after the main initializers (CORPUS-32), where upstream's
  bootstrap hook did. On Forge 61, and on Forge 47 for item colours and particles too, the remapping of block colours
  by id, which Forge does its own way; on Forge 47, upgrading a world from before 1.18 with other mods' structures.
- **Fluid rendering.** A Fabric fluid render handler's overlay sprite on the sides of a fluid beside glass or leaves;
  Forge's own overlay drawing stays (all five).
- **Forge 47's interface mixins.** Its Mixin (0.8.5) applies no injector in an interface mixin. Fabricated applies an
  `@Inject` at `HEAD`, `RETURN` or `TAIL` there itself (INTERFACE-1): the oxidizable blocks registry's (the test mod's
  pair is in vanilla's oxidation map, server run) and `TooltipComponentCallback`'s (rendering-v1, its shape in core's
  tests). Other injectors in an interface mixin, such as the recipe remainder's above, are lost there.
- **Sound (Forge 47).** A Fabric sound instance's own audio stream (`FabricSoundInstance.getAudioStream`).
- **Other hooks inside the HUD method.** A Fabric mod's mixin inside vanilla's HUD method (`Gui.render`; on Forge 64
  `Gui.extractRenderState`, on 65 and 66 `Hud.extractRenderState`) that wraps or redirects one of vanilla's calls,
  hooks in at a field, a constant or a jump, or at a call cancels, captures locals or takes arguments the called
  method doesn't, applies but never runs: Forge 61 to 66 skip that code, and on Forge 47 only hooks at the method's
  start and end run (HUD-4, above). Mixins into the methods that draw each part, which Forge's layers call, run.
- **Data generation only.** Removing entries through a Fabric tag appender, Fabric's key sort orders in generated files,
  and, by version, the advancement builder's Fabric interface and vanilla's block loot provider limited to vanilla's
  blocks; a Fabric model provider also writes item definitions for other mods' block items.
