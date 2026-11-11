package io.github.cmdralph.fabricated.forgetest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The forge-legacy test mod's part for the injectors of interface mixins that Fabricated applies where Forge 47's Mixin
 * applies none in an interface mixin (INTERFACE-1): {@code mixin/ContainerInterfaceMixin}'s {@code @Inject} at a call
 * capturing locals, {@code @WrapOperation}, {@code @Redirect} taking a {@code @Local} and {@code @ModifyConstant}, into
 * {@code Container}'s default methods, which act on {@link TestContainer} alone; another container keeps vanilla's.
 */
public final class ForgeInterfaceInjectorTest {
	/** What the mixin's handlers saw. */
	public static final List<String> SEEN = new ArrayList<>();

	private ForgeInterfaceInjectorTest() {
	}

	/** The container the mixin's handlers act on. */
	public static final class TestContainer extends SimpleContainer {
		TestContainer(ItemStack... stacks) {
			super(stacks);
		}
	}

	/** At the first server tick. */
	public static void check() {
		TestContainer container = new TestContainer(new ItemStack(Items.STICK, 3), new ItemStack(Items.DIRT), new ItemStack(Items.STICK, 2));
		SimpleContainer plain = new SimpleContainer(new ItemStack(Items.STICK, 3), new ItemStack(Items.DIRT), new ItemStack(Items.STICK, 2));
		SEEN.clear();
		int counted = container.countItem(Items.STICK);
		List<String> slots = List.copyOf(SEEN);
		report("interface inject", slots.equals(List.of("slot 0 after 0", "slot 1 after 30", "slot 2 after 30")) ? null : "countItem's handler saw " + slots);
		report("interface wrap operation", counted == 50 && plain.countItem(Items.STICK) == 5 ? null
				: "countItem counts " + counted + " sticks, and " + plain.countItem(Items.STICK) + " in another container");
		SEEN.clear();
		boolean any = container.hasAnyMatching(stack -> false);
		List<String> tests = List.copyOf(SEEN);
		report("interface redirect", any && tests.equals(List.of("test 0", "test 1")) && !plain.hasAnyMatching(stack -> false) ? null
				: "hasAnyMatching answers " + any + ", its redirector saw " + tests);
		report("interface constant", container.getMaxStackSize() == 7 && plain.getMaxStackSize() == 64 ? null
				: "getMaxStackSize answers " + container.getMaxStackSize() + ", and " + plain.getMaxStackSize() + " for another container");
	}

	private static void report(String check, String failed) {
		if (failed == null) ForgeTestMod.LOGGER.info("FABRICATED-TEST {} OK", check);
		else ForgeTestMod.LOGGER.error("FABRICATED-TEST {} FAILED: {}", check, failed);
	}
}
