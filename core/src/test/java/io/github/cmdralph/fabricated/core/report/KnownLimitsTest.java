package io.github.cmdralph.fabricated.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.mixin.PreflightFinding;
import io.github.cmdralph.fabricated.core.mixin.PreflightFinding.Severity;
import io.github.cmdralph.fabricated.core.runtime.EntrypointRunner;

/** The known limits the report explains, matched on the lines of the release's runs. */
class KnownLimitsTest {
	private static final String SPAWN_HOOK = "@Redirect in serverTick expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/entity/Mob;checkSpawnRules("
			+ "Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/EntitySpawnReason;)Z\", ordinal=0), which does not occur in that method";

	private static PreflightFinding finding(Severity severity, String message) {
		return new PreflightFinding(severity, "mod.mixins.json", "com.example.mixin.SpawnerMixin", message, "the mod needs an update", "handler()V");
	}

	private static String registeredTwice(String hash, String otherHash) {
		return "The object PoiType[matchingStates=[], maxTickets=0, validRange=1]{" + hash + "} has been registered twice, using the names"
				+ " wover:portal and wover:story_altar. (Other object at this id is PoiType[matchingStates=[], maxTickets=0, validRange=1]{" + otherHash + "})";
	}

	@Test
	void aSpawnCheckHookIsSpawn1OnEveryPlatform() {
		String forge47 = "@Redirect in m_151311_ expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/entity/Mob;m_6914_(Lnet/minecraft/world/level/LevelReader;)Z\","
				+ " ordinal=0), which does not occur in that method";
		String limit = KnownLimits.ofFinding("NeoForge", finding(Severity.LEFT_OUT, SPAWN_HOOK));
		assertEquals("Known limit: NeoForge and Forge run spawn checks through their own event, so Fabric hooks of those checks can't apply;"
				+ " use the mod's NeoForge or Forge build (SPAWN-1)", limit);
		assertEquals(limit, KnownLimits.ofFinding("Forge", finding(Severity.LEFT_OUT, forge47)));
		assertEquals(limit, KnownLimits.ofFinding("Forge", finding(Severity.FAIL, SPAWN_HOOK)), "the hook disables its mod");
	}

	/** A @Group's member that can't apply is a warning, whether or not its group applies: only the group's failure leaves hooks out. */
	@Test
	void aWarningIsNoKnownLimit() {
		assertNull(KnownLimits.ofFinding("NeoForge", finding(Severity.WARN, SPAWN_HOOK)));
		assertNull(KnownLimits.ofFinding("NeoForge", finding(Severity.INFO, SPAWN_HOOK)));
	}

	@Test
	void aScreenRenderWrapperIsScreen2OnForgeOnly() {
		PreflightFinding wrapper = finding(Severity.LEFT_OUT, "@WrapOperation in extractRenderState expects @At(\"INVOKE\", target=\"Lnet/minecraft/client/gui/screens/Screen;"
				+ "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V\", ordinal=0), which does not occur in that method");
		assertTrue(KnownLimits.ofFinding("Forge", wrapper).endsWith("(SCREEN-2)"));
		assertNull(KnownLimits.ofFinding("NeoForge", wrapper), "NeoForge's hook takes the screen: a wrapper left out there is left out for another reason");
	}

	/** A hook of vanilla's friction in Leashable's angularFriction, an interface's method, which pre-flight's hint says isn't put back. */
	@Test
	void aHookOfLeashablesFrictionIsSeam8() {
		PreflightFinding hook = new PreflightFinding(Severity.LEFT_OUT, "mod.mixins.json", "com.example.mixin.LeashMixin", "@WrapOperation in angularFriction expects"
				+ " @At(\"INVOKE\", target=\"Lnet/minecraft/world/level/block/Block;getFriction()F\"), which does not occur in that method - Leashable's"
				+ " angularFriction asks the block state's getFriction(level, pos, entity) instead, and Leashable is an interface, which can't keep what"
				+ " Fabricated's check of a block's class answers, so Fabricated doesn't put vanilla's call back there (SEAM-8).", "", "handler()V");
		assertTrue(KnownLimits.ofFinding("NeoForge", hook).endsWith("(SEAM-8)"));
		assertTrue(KnownLimits.ofFinding("Forge", hook).endsWith("(SEAM-8)"));
		// A hook of vanilla's friction left out elsewhere is no such limit.
		assertNull(KnownLimits.ofFinding("NeoForge", new PreflightFinding(Severity.LEFT_OUT, "mod.mixins.json", "com.example.mixin.LeashMixin", "@WrapOperation in"
				+ " travel expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/level/block/Block;getFriction()F\"), which does not occur in that method", "",
				"handler()V")));
	}

	@Test
	void aHookInVanillasSweepAttackNeoForgeNeverCallsIsSeam8() {
		String message = "@WrapOperation sweep applies in doSweepAttack(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;F)V,"
				+ " vanilla's method, which NeoForge's Player never calls: it makes the sweep attack's box in attack through the stack's"
				+ " getSweepHitBox(player, target) and hands it to an overload of doSweepAttack of its own, so the hook never runs; Fabricated doesn't put"
				+ " vanilla's call back there (SEAM-8)";
		PreflightFinding note = new PreflightFinding(Severity.INFO, "mod.mixins.json", "com.example.mixin.SweepMixin", message, "", "sweep()V");
		assertTrue(KnownLimits.ofFinding("NeoForge", note).endsWith("apply there but never run (SEAM-8)"));
		assertNull(KnownLimits.ofFinding("Forge", note));
		// It is a note of a hook that applies: a finding leaving one out with that message is no such limit, nor is another note.
		assertNull(KnownLimits.ofFinding("NeoForge", new PreflightFinding(Severity.LEFT_OUT, "mod.mixins.json", "com.example.mixin.SweepMixin", message, "",
				"sweep()V")));
		assertNull(KnownLimits.ofFinding("NeoForge", new PreflightFinding(Severity.INFO, "mod.mixins.json", "com.example.mixin.SweepMixin", "@WrapOperation"
				+ " sweep hooks AABB.inflate(DDD)Lnet/minecraft/world/phys/AABB; in attack (SEAM-8)", "", "sweep()V")));
		// Nor is a note a limit of a hook left out.
		assertNull(KnownLimits.ofFinding("NeoForge", new PreflightFinding(Severity.INFO, "mod.mixins.json", "com.example.mixin.LeashMixin", "@WrapOperation in"
				+ " angularFriction expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/level/block/Block;getFriction()F\") - Leashable is an interface",
				"", "handler()V")));
	}

	/**
	 * SEAM-12: on Forge, pre-flight's note of a hook of vanilla's farmland check in a crop's growth speed, which goes back for
	 * the CropBlocks keeping their checks, names the stems and the pitcher crop it doesn't run for; by Mojang's names and
	 * Forge 47's. Not NeoForge's note (its condition is the soil's, every plant's), nor the moisture check's (any plant's on
	 * farmland), nor the note of a class a native mixin keeps, nor a hook left out with that message.
	 */
	@Test
	void aHookOfVanillasFarmlandCheckOnForgeNamesTheStemsAndThePitcherCrop() {
		String forge = "@ModifyVariable capture hooks BlockState.is(Lnet/minecraft/world/level/block/Block;)Z in getGrowthSpeed, vanilla's call Forge's CropBlock"
				+ " makes through the soil state's canSustainPlant(level, pos, facing, plantable) instead; Fabricated puts it back there for the soils"
				+ " keeping that method's default (SEAM-12)";
		for (String message : List.of(forge, forge.replace("BlockState.is(", "BlockState.m_60713_(").replace("in getGrowthSpeed,", "in m_52272_,"))) {
			String limit = KnownLimits.ofFinding("Forge", new PreflightFinding(Severity.INFO, "balm.mixins.json", "net.blay09.mods.balm.mixin.FabricCropBlockMixin",
					message, "", "capture()V"));
			assertTrue(limit != null && limit.contains("a stem's growth and, from Minecraft 1.20, the pitcher crop's") && limit.endsWith("(SEAM-12)"),
					message + ": " + limit);
		}
		// Not the note of a class a native mod's mixin keeps as Forge has it, where the hook runs for no crop.
		String kept = "@ModifyVariable capture hooks BlockState.is(Lnet/minecraft/world/level/block/Block;)Z in getGrowthSpeed, vanilla's call Forge's CropBlock"
				+ " makes through the soil state's canSustainPlant(level, pos, facing, plantable) instead, which Fabricated doesn't put back there, so it finds"
				+ " nothing to hook, as before SEAM-12: Forge mod breaker's mixin NativeBreak hooks the platform's code that putting vanilla's calls back would"
				+ " move, CropBlock.mayPlaceOn (SEAM-12)";
		assertNull(KnownLimits.ofFinding("Forge", new PreflightFinding(Severity.INFO, "balm.mixins.json", "net.blay09.mods.balm.mixin.FabricCropBlockMixin",
				kept, "", "capture()V")));
		assertNull(KnownLimits.ofFinding("NeoForge", new PreflightFinding(Severity.INFO, "balm.mixins.json", "net.blay09.mods.balm.mixin.FabricCropBlockMixin",
				forge.replace("Forge's", "NeoForge's"), "", "capture()V")));
		assertNull(KnownLimits.ofFinding("Forge", new PreflightFinding(Severity.INFO, "balm.mixins.json", "net.blay09.mods.balm.mixin.FabricCropBlockMixin",
				forge.replace("canSustainPlant(level, pos, facing, plantable)", "isFertile(level, pos)"), "", "capture()V")));
		assertNull(KnownLimits.ofFinding("Forge", new PreflightFinding(Severity.LEFT_OUT, "balm.mixins.json", "net.blay09.mods.balm.mixin.FabricCropBlockMixin",
				forge, "", "capture()V")));
	}

	@Test
	void aHookOfTheServersPlayerWillDestroyCallIsSeam3OnForgeOnly() {
		String message = "@ModifyExpressionValue in m_9280_ expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/level/block/Block;"
				+ "m_5707_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;"
				+ "Lnet/minecraft/world/entity/player/Player;)V\"), which does not occur in that method";
		// Pre-flight's hint names the server's class, where the call isn't put back.
		PreflightFinding forge47 = new PreflightFinding(Severity.LEFT_OUT, "mod.mixins.json", "com.example.mixin.BreakMixin", message,
				"Forge's ServerPlayerGameMode makes that call through the block's IForgeBlock.onDestroyedByPlayer instead, which Fabricated doesn't"
						+ " put back there (SEAM-3).", "handler()V");
		assertTrue(KnownLimits.ofFinding("Forge", forge47).endsWith("(SEAM-3)"), KnownLimits.ofFinding("Forge", forge47));
		assertNull(KnownLimits.ofFinding("NeoForge", forge47), "NeoForge keeps the call in destroyBlock: a hook left out there is left out for another reason");
		// The client's destroyBlock (Forge 52 on) left out for another reason, where the call goes back, is none.
		assertNull(KnownLimits.ofFinding("Forge", finding(Severity.LEFT_OUT, message.replace("m_9280_", "destroyBlock").replace("m_5707_", "playerWillDestroy"))));
	}

	@Test
	void aHookOfTheHeldItemsCheckInDestroyBlockIsSeam4WhereThePlatformAsksItInItsBreakEvent() {
		// Porting Lib's, on Forge 47 under SRG's names.
		String message = "@ModifyExpressionValue in m_9280_ expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/item/Item;m_6777_("
				+ "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
				+ "Lnet/minecraft/world/entity/player/Player;)Z\"), which does not occur in that method";
		PreflightFinding forge47 = new PreflightFinding(Severity.LEFT_OUT, "mod.mixins.json", "com.example.mixin.BreakMixin", message,
				"Forge's ServerPlayerGameMode asks the held item's canAttackBlock inside its block break event (ForgeHooks.onBlockBreakEvent), which can"
						+ " overrule the answer, so Fabricated doesn't put that check back there (SEAM-4).", "handler()V");
		assertTrue(KnownLimits.ofFinding("Forge", forge47).endsWith("(SEAM-4)"), KnownLimits.ofFinding("Forge", forge47));
		// NeoForge 26.1.2's client, the stack's check.
		PreflightFinding client = new PreflightFinding(Severity.LEFT_OUT, "mod.mixins.json", "com.example.mixin.ClientBreakMixin", "@WrapOperation in destroyBlock"
				+ " expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/item/ItemStack;canDestroyBlock(Lnet/minecraft/world/level/block/state/BlockState;"
				+ "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z\"), which does not occur in that method",
				"NeoForge's MultiPlayerGameMode asks the held item's canDestroyBlock inside its block break event (CommonHooks.fireBlockBreak), which can"
						+ " overrule the answer, so Fabricated doesn't put that check back there (SEAM-4).", "handler()V");
		assertTrue(KnownLimits.ofFinding("NeoForge", client).endsWith("(SEAM-4)"), KnownLimits.ofFinding("NeoForge", client));
		// The same call left out for another reason, without the hint (NeoForge 21.1's client keeps it), is none.
		assertNull(KnownLimits.ofFinding("NeoForge", finding(Severity.LEFT_OUT, message)));
	}

	/**
	 * Ledger's hooks of a sheep's and a snow golem's shear call in their mobInteract, which the platforms leave to the shears
	 * item with the shears check guarding it, are SEAM-6 as the hooks of that check are, where pre-flight's hint says so.
	 */
	@Test
	void aHookOfAMobsShearCallIsSeam6WherePreflightsHintSaysThePlatformShearsFromTheShearsItem() {
		String check = "@WrapOperation in mobInteract expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z\"),"
				+ " which does not occur in that method";
		String hint = "%s's mobInteract asks for no shears here: the platform shears it from the shears item, through its shearable extension (NeoForge's"
				+ " IShearable, Forge's IForgeShearable), which posts its events and drops what its loot says, so Fabricated doesn't put vanilla's %s back"
				+ " there (SEAM-6).";
		String limit = KnownLimits.ofFinding("NeoForge", new PreflightFinding(Severity.LEFT_OUT, "bclib.mixins.common.json",
				"org.betterx.bclib.mixin.common.shears.SheepMixin", check, String.format(hint, "Sheep", "check"), "handler()V"));
		assertTrue(limit.endsWith("(SEAM-6)"), limit);
		// NeoForge 21.1 and Forge 52 under Mojang's names, Forge 47 under SRG's.
		for (String mob : List.of("Sheep", "SnowGolem")) {
			String message = "@Inject in mobInteract expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/entity/animal/" + mob
					+ ";shear(Lnet/minecraft/sounds/SoundSource;)V\"), which does not occur in that method";
			for (String m : List.of(message, message.replace("mobInteract", "m_6071_").replace(";shear(", ";m_5851_("))) {
				PreflightFinding shear = new PreflightFinding(Severity.LEFT_OUT, "ledger.mixins.json", "com.github.quiltservertools.ledger.mixin.entities." + mob
						+ "EntityMixin", m, String.format(hint, mob, "check and its shear call"), "handler()V");
				assertEquals(limit, KnownLimits.ofFinding("NeoForge", shear), m);
				assertEquals(limit, KnownLimits.ofFinding("Forge", shear), m);
			}
			// Left out for another reason, without the hint (Forge 65 and 66 shear there), it is none.
			assertNull(KnownLimits.ofFinding("Forge", finding(Severity.LEFT_OUT, message)), message);
		}
	}

	@Test
	void twoEqualEntriesRegisteredOnForgeAreReg13() {
		IllegalArgumentException thrown = new IllegalArgumentException(registeredTwice("36b858fa", "4bef7d97"));
		EntrypointRunner.Failure failure = new EntrypointRunner.Failure("wover_story", "main", "org.example.Story", new RuntimeException("wrapped", thrown));
		assertTrue(failure.describe().endsWith("{4bef7d97}). Known limit: Forge's registries take two equal entries for one, so the mod's second one"
				+ " can't be registered; use NeoForge or the mod's Forge build (REG-13)"), failure.describe());
	}

	/** One object registered under two names has the same identity hash twice: the mod's own mistake, which the hint would hide. */
	@Test
	void oneObjectRegisteredTwiceIsNotReg13() {
		assertNull(KnownLimits.ofFailure(new IllegalArgumentException(registeredTwice("36b858fa", "36b858fa"))));
	}

	/** One line of the release's reports: the finding it prints, as pre-flight made it. */
	private static final Pattern REPORTED = Pattern.compile("^[\\w-]+: (LEFT_OUT|FAIL) \\[(\\S+) (\\S+)] (.*?)(?: - (.*))?$");

	private static PreflightFinding reported(String line) {
		Matcher m = REPORTED.matcher(line);
		assertTrue(m.matches(), line);
		return new PreflightFinding(Severity.valueOf(m.group(1)), m.group(2), m.group(3), m.group(4), m.group(5) == null ? "" : m.group(5), "handler()V");
	}

	private record Reported(String loader, String limit, PreflightFinding finding) {
	}

	/** The lines of {@code release-left-out.txt}: each with its platform and the finding of the known limit it is, or none. */
	private static List<Reported> releaseLines() throws IOException {
		List<Reported> lines = new ArrayList<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(KnownLimitsTest.class.getResourceAsStream("release-left-out.txt"), StandardCharsets.UTF_8))) {
			for (String line = in.readLine(); line != null; line = in.readLine()) {
				if (line.startsWith("#")) continue;
				String[] parts = line.split("\t", 3);
				lines.add(new Reported(parts[0], parts[1], reported(parts[2])));
			}
		}
		return lines;
	}

	/**
	 * LIMIT-3: the documented limits of one mod's hooks, each matched on the release's own lines (Additional Entity
	 * Attributes' water speed, FLUID-5; Reach Entity Attributes and Porting Lib on Forge 47, ATTR-2 and HOOK-5; Ledger's
	 * campfire hook, LOCAL-6; OneConfig's key mapping accessor, KEYMAP-1; Farmer's Delight's cactus, mushroom and stem
	 * hooks, SEAM-12; FrozenLib's and Lithium's hopper hooks, SEAM-14; the fluid hooks of Lithium, Wilder Wild, FrozenLib,
	 * Expandability, Milk Lib and, on Forge, Fabric API's content registries, SEAM-9), where the report named none. The
	 * lines next to them stay as they were: Supplementaries' invoker on Forge 47 (STRUCT-2), Porting Lib's hooks another
	 * limit names (SEAM-4, SEAM-6), its GUI utilities' failure (CORPUS-93) and Additional Entity Attributes' dig speed
	 * hook. No limit names the hooks whose vanilla calls go back now (Balm's and Farmer's Delight's of a crop's growth
	 * speed, SEAM-12; Artifacts' of the mining speed's eye check and FrozenLib's of the air bubbles', SEAM-9), nor
	 * Supplementaries' invoker on NeoForge 21.1 and Forge 52, which a bridge now serves (LEAVE-6): its line has no reason
	 * the bridge can't serve it.
	 */
	@Test
	void theDocumentedLimitsOfOneModsHooksAreNamedOnTheReleasesLines() throws IOException {
		List<Reported> lines = releaseLines();
		assertEquals(144, lines.size());
		for (Reported r : lines) {
			String limit = KnownLimits.ofFinding(r.loader(), r.finding());
			if (r.limit().equals("-")) {
				assertNull(limit, r.finding().toString());
			} else {
				assertTrue(limit != null && limit.endsWith("(" + r.limit() + ")"), r.finding() + " -> " + limit);
			}
		}
	}

	/** Each limit of one mod's hooks names only that mod's mixins, on the platforms it is on, for the reason it gives. */
	@Test
	void aLimitOfOneModsHooksMatchesNoOtherMixinPlatformOrReason() throws IOException {
		for (Reported r : releaseLines()) {
			PreflightFinding f = r.finding();
			if (r.limit().equals("-") || r.limit().startsWith("SEAM") || r.limit().equals("STRUCT-2")) continue;
			PreflightFinding otherMod = new PreflightFinding(f.severity(), "other.mixins.json", "com.example.mixin." + f.mixin().substring(f.mixin().lastIndexOf('.') + 1),
					f.message(), f.suggestion(), f.handler());
			assertNull(KnownLimits.ofFinding(r.loader(), otherMod), otherMod.toString());
			PreflightFinding otherReason = new PreflightFinding(f.severity(), f.config(), f.mixin(), "@Inject in tick expects @At(\"INVOKE\", target="
					+ "\"Lnet/minecraft/world/entity/Entity;tick()V\"), which does not occur in that method", "", f.handler());
			assertNull(KnownLimits.ofFinding(r.loader(), otherReason), otherReason.toString());
		}
		Reported keymap = releaseLines().stream().filter(r -> r.limit().equals("KEYMAP-1")).findFirst().orElseThrow();
		assertNull(KnownLimits.ofFinding("Forge", keymap.finding()), "Forge keeps vanilla's map");
		for (Reported forge47 : releaseLines().stream().filter(r -> r.limit().equals("ATTR-2") || r.limit().equals("HOOK-5")).toList()) {
			assertNull(KnownLimits.ofFinding("NeoForge", forge47.finding()), forge47.finding().toString());
		}
		// Porting Lib's hook of a game method in Mojang's names is another version's, where Forge's code isn't Forge 47's.
		assertNull(KnownLimits.ofFinding("Forge", new PreflightFinding(Severity.LEFT_OUT, "porting_lib_base.mixins.json",
				"io.github.fabricators_of_create.porting_lib.mixin.common.ShapedRecipeMixin", "@ModifyConstant in patternFromJson expects the constant 3, which"
						+ " does not occur in that method", "Forge or this Minecraft version changed the method body", "handler()V")));
		// Supplementaries' other invoker in the mixin, and another mixin's invoker of the method, are none.
		assertNull(KnownLimits.ofFinding("NeoForge", new PreflightFinding(Severity.LEFT_OUT, "supplementaries.mixins.json",
				"net.mehvahdjukaar.supplementaries.mixins.fabric.FireBlockAccessor", "@Invoker target method canBurn(Lnet/minecraft/world/level/block/state/BlockState;)Z"
						+ " does not exist in net.minecraft.world.level.block.FireBlock", "The method was renamed or removed by NeoForge or this Minecraft version.",
				"invokeCanBurn(Lnet/minecraft/world/level/block/state/BlockState;)Z")));
	}

	/**
	 * LAMBDA-14 and CORPUS-57: the screen input hooks the platforms' own handlers leave nothing for, on the release's own
	 * lines: wrappers of Forge 52's mouse calls, which its event factory makes, with owo-lib's hook keeping their key
	 * modifiers; JEI's wrapper of setKey in the Controls screen's reset of every key, which the platforms make with their
	 * setToDefault; and Architectury's modifier of the listener the platforms' char typed lambdas take as the screen.
	 */
	@Test
	void theScreenInputHooksThePlatformsHandlersLeaveNothingForAreNamedOnTheReleasesLines() {
		String forgeChanged = "Forge or this Minecraft version changed the method body; the mod needs an update or this mixin must be skipped.";
		for (String line : List.of(
				"owo: LEFT_OUT [owo.mixins.json io.wispforest.owo.mixin.ui.layers.MouseMixin] @WrapOperation in lambda$onPress$0 expects @At(\"INVOKE\","
						+ " target=\"Lnet/minecraft/client/gui/screens/Screen;mouseClicked(DDI)Z\"), which does not occur in that method - " + forgeChanged,
				"owo: LEFT_OUT [owo.mixins.json io.wispforest.owo.mixin.ui.layers.MouseMixin] @WrapOperation in lambda$onPress$1 expects @At(\"INVOKE\","
						+ " target=\"Lnet/minecraft/client/gui/screens/Screen;mouseReleased(DDI)Z\"), which does not occur in that method - " + forgeChanged,
				"architectury: LEFT_OUT [architectury.mixins.json dev.architectury.mixin.fabric.client.MixinMouseHandler] @Redirect in"
						+ " lambda$handleAccumulatedMovement$11 expects @At(\"INVOKE\", target=\"Lnet/minecraft/client/gui/screens/Screen;mouseDragged(DDIDD)Z\"),"
						+ " which does not occur in that method - " + forgeChanged,
				"owo: LEFT_OUT [owo.mixins.json io.wispforest.owo.mixin.ui.layers.MouseMixin] The injector of"
						+ " captureMods(JIIILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V goes with that of"
						+ " passModifiersToBraid$mouseClicked(Lnet/minecraft/client/gui/screens/Screen;DDILcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Z,"
						+ " which can't apply: they keep state together in this mixin's field MODS")) {
			String limit = KnownLimits.ofFinding("Forge", reported(line));
			assertTrue(limit != null && limit.endsWith("(LAMBDA-14)") && limit.contains("Forge 52"), line + " -> " + limit);
			assertNull(KnownLimits.ofFinding("NeoForge", reported(line)), "NeoForge hands the screen its mouse input in the lambdas");
			// Another mod's line is none: the limit names owo-lib's and Architectury's hooks.
			assertNull(KnownLimits.ofFinding("Forge", reported(otherMod(line))), line);
		}
		// Nor an @Inject at the call, which the limit's text doesn't say: only wrappers and redirects take the screen's answer.
		assertNull(KnownLimits.ofFinding("Forge", reported("owo: LEFT_OUT [owo.mixins.json io.wispforest.owo.mixin.ui.layers.MouseMixin] @Inject in lambda$onPress$0"
				+ " expects @At(\"INVOKE\", target=\"Lnet/minecraft/client/gui/screens/Screen;mouseClicked(DDI)Z\"), which does not occur in that method - " + forgeChanged)));
		// owo-lib's hook keeping the modifiers, left out with another of its injectors, or another mod's, is none.
		assertNull(KnownLimits.ofFinding("Forge", reported("owo: LEFT_OUT [owo.mixins.json io.wispforest.owo.mixin.ui.layers.MouseMixin] The injector of"
				+ " captureMods(JIIILorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V goes with that of captureScreenMouseDrag(DD)V,"
				+ " which can't apply: they keep state together in this mixin's field MODS")));
		for (String loader : List.of("NeoForge", "Forge")) {
			String changed = loader + " or this Minecraft version changed the method body; the mod needs an update or this mixin must be skipped.";
			for (String line : List.of(
					"jei: LEFT_OUT [jei.mixins.json mezz.jei.fabric.mixin.KeyBindsScreenMixin] @WrapOperation in lambda$addFooter$0 expects @At(\"INVOKE\","
							+ " target=\"Lnet/minecraft/client/KeyMapping;setKey(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V\"), which does not occur in that"
							+ " method - " + changed,
					"jei: LEFT_OUT [jei.mixins.json mezz.jei.fabric.mixin.KeyBindsScreenMixin] @WrapOperation in m_268771_ expects @At(\"INVOKE\","
							+ " target=\"Lnet/minecraft/client/KeyMapping;m_90848_(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V\"), which does not occur in that"
							+ " method - " + changed)) {
				String limit = KnownLimits.ofFinding(loader, reported(line));
				assertTrue(limit != null && limit.endsWith("(LAMBDA-14)") && limit.contains("setToDefault"), line + " -> " + limit);
			}
			String declared = loader + "'s build of Minecraft declares this method with different parameters; the mod needs an update or this mixin must be skipped.";
			for (String line : List.of(
					"architectury: LEFT_OUT [architectury.mixins.json dev.architectury.mixin.fabric.client.MixinKeyboardHandler] @ModifyVariable wrapCharTypedFirst"
							+ " modifies argument 0 among those of type net.minecraft.client.gui.components.events.GuiEventListener of"
							+ " lambda$charTyped$6(Lnet/minecraft/client/gui/screens/Screen;II)V in net.minecraft.client.KeyboardHandler, which that method doesn't have - "
							+ declared,
					"architectury: LEFT_OUT [architectury.mixins.json dev.architectury.mixin.fabric.client.MixinKeyboardHandler] @ModifyVariable wrapCharTypedSecond"
							+ " modifies argument 0 among those of type net.minecraft.client.gui.components.events.GuiEventListener of"
							+ " lambda$charTyped$7(Lnet/minecraft/client/gui/screens/Screen;CI)V in net.minecraft.client.KeyboardHandler, which none of those methods has - "
							+ declared)) {
				String limit = KnownLimits.ofFinding(loader, reported(line));
				assertTrue(limit != null && limit.endsWith("(CORPUS-57)"), line + " -> " + limit);
				assertNull(KnownLimits.ofFinding(loader, reported(otherMod(line))), line);
			}
		}
		// A hook of setKey elsewhere, or a modifier of another listener, is none.
		assertNull(KnownLimits.ofFinding("NeoForge", finding(Severity.LEFT_OUT, "@WrapOperation in lambda$new$2 expects @At(\"INVOKE\","
				+ " target=\"Lnet/minecraft/client/KeyMapping;setKey(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V\"), which does not occur in that method")));
		assertNull(KnownLimits.ofFinding("NeoForge", finding(Severity.LEFT_OUT, "@ModifyVariable wrap modifies argument 0 among those of type"
				+ " net.minecraft.client.gui.components.events.GuiEventListener of lambda$charTyped$6(Lnet/minecraft/client/gui/components/events/GuiEventListener;II)V"
				+ " in net.minecraft.client.KeyboardHandler, which that method doesn't have")));
		// Nor another mod's modifier in any Forge 47 method taking a screen first.
		assertNull(KnownLimits.ofFinding("Forge", finding(Severity.LEFT_OUT, "@ModifyVariable wrap modifies argument 0 among those of type"
				+ " net.minecraft.client.gui.components.events.GuiEventListener of m_90889_(Lnet/minecraft/client/gui/screens/Screen;II)V"
				+ " in net.minecraft.client.KeyboardHandler, which that method doesn't have")));
	}

	/** A report's line with its mod, config and mixin another mod's. */
	private static String otherMod(String line) {
		return line.replaceFirst("^\\S+: (\\w+) \\[\\S+ \\S+\\]", "other: $1 [other.mixins.json com.example.mixin.OtherMixin]");
	}

	/**
	 * HUD-8 and SCREEN-3: the release's lines of hooks NeoForge's own HUD layers leave nothing for (vanilla's hotbar and
	 * player health methods, which it splits into layers, and its drawing of vanilla's layer stacks), with the hooks keeping
	 * state with them, and of Forge 61 to 66's item tooltip overload, each named; on the other platform none.
	 */
	@Test
	void theHudAndTooltipHooksThePlatformsOwnCodeLeavesNothingForAreNamedOnTheReleasesLines() throws IOException {
		List<String[]> lines = new ArrayList<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(KnownLimitsTest.class.getResourceAsStream("release-screens-hud.txt"), StandardCharsets.UTF_8))) {
			for (String line = in.readLine(); line != null; line = in.readLine()) {
				if (!line.startsWith("#")) lines.add(line.split("\t", 3));
			}
		}
		assertEquals(9, lines.size());
		for (String[] line : lines) {
			PreflightFinding finding = reported(line[2]);
			String limit = KnownLimits.ofFinding(line[0], finding);
			assertTrue(limit != null && limit.endsWith("(" + line[1] + ")"), line[2] + " -> " + limit);
			assertNull(KnownLimits.ofFinding(line[0].equals("Forge") ? "NeoForge" : "Forge", finding), line[2]);
			// The player health limit names Apoli's, GlitchCore's and Puzzles Lib's hooks: another mod's line is none.
			if (line[2].contains("PlayerHealth expects")) assertNull(KnownLimits.ofFinding(line[0], reported(otherMod(line[2]))), line[2]);
			// The tooltip limit says wrappers and redirects: an @Inject at the call is none.
			if (line[1].equals("SCREEN-3")) assertNull(KnownLimits.ofFinding(line[0], reported(line[2].replace("] @Redirect in ", "] @Inject in "))), line[2]);
		}
		// Another mod's hook keeping state with one of those, or another hook in vanilla's Gui, is none.
		assertNull(KnownLimits.ofFinding("NeoForge", reported("other: LEFT_OUT [other.mixins.json com.example.mixin.GuiMixin] The injector of onRender(Lnet/minecraft/"
				+ "client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V goes with that of"
				+ " onRenderPlayerHealth(Lnet/minecraft/client/gui/GuiGraphics;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V, which can't apply:"
				+ " they keep state together in this mixin's field state")));
		assertNull(KnownLimits.ofFinding("NeoForge", finding(Severity.LEFT_OUT, "@Inject in renderCrosshair expects @At(\"INVOKE\", target=\"Lnet/minecraft/"
				+ "client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V\"), which does not occur in that method")));
	}

	/**
	 * SCREEN-3: FancyMenu's condition on vanilla's drawing of the version string in the title screen, which NeoForge 21.1
	 * draws as its branding lines (the release's line); another version's call, another platform or another mod is none.
	 */
	@Test
	void fancyMenusTitleScreenVersionTextHookIsNamedOnNeoForge21() {
		String line = "fancymenu: LEFT_OUT [fancymenu.fabric.mixins.json de.keksuccino.fancymenu.mixin.mixins.fabric.client.MixinFabricTitleScreen]"
				+ " @WrapWithCondition in render expects @At(\"INVOKE\", target=\"Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;"
				+ "Ljava/lang/String;III)I\"), which does not occur in that method - NeoForge or this Minecraft version changed the method body; the mod needs an"
				+ " update or this mixin must be skipped.";
		String limit = KnownLimits.ofFinding("NeoForge", reported(line));
		assertTrue(limit != null && limit.endsWith("(SCREEN-3)") && limit.contains("BrandingControl"), limit);
		assertNull(KnownLimits.ofFinding("Forge", reported(line)));
		assertNull(KnownLimits.ofFinding("NeoForge", reported(otherMod(line))));
		assertNull(KnownLimits.ofFinding("NeoForge", reported(line.replace("III)I", "III)V"))), "1.21.11's call isn't the one judged");
	}

	@Test
	void anythingElseIsNoKnownLimit() {
		for (String message : List.of(
				"@Redirect in serverTick expects @At(\"INVOKE\", target=\"Lnet/minecraft/world/entity/Mob;finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;)V\")",
				"@WrapOperation in extractGui expects @At(\"INVOKE\", target=\"Lnet/minecraft/client/gui/screens/Screen;"
						+ "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V\")")) {
			assertNull(KnownLimits.ofFinding("Forge", finding(Severity.LEFT_OUT, message)), message);
		}
		assertNull(KnownLimits.ofFailure(new IllegalStateException(registeredTwice("1", "2"))), "another class");
		assertNull(KnownLimits.ofFailure(new IllegalArgumentException("The name wover:portal has been registered twice, for a and b.")));
		assertNull(KnownLimits.ofFailure(new IllegalArgumentException((String) null)));
		assertEquals("m: main entrypoint E threw java.lang.IllegalArgumentException: bad",
				new EntrypointRunner.Failure("m", "main", "E", new IllegalArgumentException("bad")).describe());
	}
}
