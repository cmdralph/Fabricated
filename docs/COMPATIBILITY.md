# Compatibility

Everything in this file comes from CI runs on a real NeoForge dedicated server (`ci/smoke-server.sh`), not
from expectations. CI boots each version twice:

1. **Test mods only**: Fabricated plus the Fabric test mods in `mc/common/src/testmod*`. This is a pass/fail
   gate.
2. **With the official Fabric API jar** added: informational. The resulting `fabric-bridge-report.txt` is
   kept as a CI artifact (`smoke-fabric-api-<version>`), and the tables below are taken from it.

Last updated from CI run [38101258203](https://github.com/cmdralph/Fabricated/actions/runs/38101258203)
(Fabricated 0.1.0, dedicated server).

## Minecraft versions

| Minecraft | NeoForge | Fabric API tested | Test mods | Server boots with Fabric API |
|---|---|---|---|---|
| 1.21.11 | 21.11.45 | 0.141.6+1.21.11 | ✅ all 13 checks | ✅ boots, all test-mod checks still pass |
| 26.1.2 | 26.1.2.116 | 0.155.3+26.1.2 | ✅ all 13 checks | ⏳ failed in run 38101258203 (see note) |
| 26.2 | 26.2.0.90 | 0.161.0+26.2 | ✅ all 13 checks | ⏳ failed in run 38101258203 (see note) |
| 26.3 (beta) | 26.3.0.69-beta | 0.162.0+26.3 | ✅ all 13 checks | ✅ boots, all test-mod checks still pass |

**Note on 26.1.2 and 26.2:** NeoForge stopped with "requests Mixin behavior version 0.17.0, which is older
than the lowest supported version 0.17.1". FML 11 and 12 reject Mixin behaviour versions below 0.17.1, while
FML 10 (1.21.11) accepts 0.16.5. Fabricated used a single minimum for every version. The minimum is now set
per version (`mixin_lowest` in `mc/<version>/gradle.properties`). This row will be updated after the next
CI run.

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

For 26.1.2 and 26.2 the table shows Fabricated's decision before the Mixin issue noted above.

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
| fabric-lifecycle-events-v1 | disabled | disabled | disabled | disabled |
| fabric-networking-api-v1 | disabled | disabled | disabled | disabled |
| fabric-registry-sync-v0 | disabled | disabled | disabled | disabled |
| fabric-resource-loader-v1 (and v0) | disabled | disabled | disabled | disabled |
| fabric-command-api-v2 | disabled | disabled | disabled | disabled |
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
| fabric-advancement-api-v1 | — | — | disabled | disabled |
| fabric-menu-api-v1 / screen-handler-api-v1 | disabled | disabled | disabled | disabled |
| fabric-api-lookup-api-v1 | disabled | disabled | disabled | disabled |
| fabric-convention-tags-v2 (and v1) | disabled | disabled | disabled | disabled |
| fabric-data-attachment-api-v1 | disabled | disabled | disabled | disabled |
| fabric-particles-v1 | disabled | disabled | disabled | disabled |
| fabric-recipe-api-v1 | disabled | disabled | disabled | disabled |
| fabric-creative-tab-api-v1 / item-group-api-v1 | disabled | disabled | disabled | disabled |
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
  - `fabric-lifecycle-events-v1`: NeoForge moved the `blockEntities.remove` call in
    `LevelChunk.getBlockEntity`.
- **NeoForge recompiles Minecraft, so names of synthetic members differ** (1.21.11): lambda methods such as
  `lambda$loadResources$1` and captured fields such as `val$tags` have different names in NeoForge's build.
- **The mixin targets a library class**: `fabric-dimensions-v1` patches DataFixerUpper's `TaggedChoice`,
  which NeoForge loads without mixin support.
- **Missing Minecraft code**: `fabric-command-api-v2` calls `EntitySelectorParser.getCustomFlag`, which
  only exists in Fabric's patched game.
- **Dependency cascade**: about a third of the disabled modules only fail because they require one of the
  above. For example, `fabric-particles-v1` and `fabric-recipe-api-v1` need networking, and
  `fabric-tag-api-v1` and `fabric-creative-tab-api-v1` need resource-loader.

Each of these used to crash the server before this check existed. Now each is a clear line in the report
and the server starts.

### What this means for Fabric mods

A Fabric mod works today if it needs only the Fabric Loader API and the **native** or **loads** modules
above. Most content mods also need lifecycle events, networking, registry sync, resource loading or
commands, so they are disabled with a message naming the missing module.

### Planned work, in order of impact

These modules are to be implemented natively on NeoForge's own events and APIs. Each one also unblocks the
modules that cascade from it.

1. `fabric-lifecycle-events-v1`: NeoForge server, level and chunk events. Unblocks api-lookup and
   convention-tags.
2. `fabric-networking-api-v1`: NeoForge's payload registrar. Unblocks particles and recipe-api.
3. `fabric-resource-loader-v1`: NeoForge's reload-listener and pack events. Unblocks tag-api, creative-tab
   and the v0 API.
4. `fabric-registry-sync-v0`: NeoForge already syncs registries, so this module only needs to provide its
   API. Unblocks object-builder.
5. `fabric-command-api-v2`: NeoForge's command registration events.

## Fabric mods tested

Only Fabricated's own test mods are tested in CI so far. Results for real Fabric mods will be added here as
CI covers them. To report one, see [CONTRIBUTING.md](../CONTRIBUTING.md#reporting-a-compatibility-result).
