package net.minecraft;

/**
 * {@code Fluid}: {@code method_15774} is {@code getBucket()}, {@code method_15777} {@code canBeReplacedWith(FluidState,
 * BlockGetter, BlockPos, Fluid, Direction)}, {@code method_15789} {@code getTickDelay(LevelReader)}, {@code method_15784}
 * {@code getExplosionResistance()}, {@code method_15790} {@code createLegacyBlock(FluidState)}, {@code method_15793}
 * {@code isSource(FluidState)}, {@code method_15779} {@code getAmount(FluidState)}, {@code method_15780}
 * {@code isSame(Fluid)}, {@code method_15775} {@code createFluidStateDefinition(StateDefinition.Builder)}.
 */
public abstract class class_3611 {
	protected class_3611() {
	}

	public abstract class_1792 method_15774();

	protected abstract boolean method_15777(class_3610 state, class_1922 level, class_2338 pos, class_3611 fluid, class_2350 direction);

	public abstract int method_15789(class_4538 level);

	protected abstract float method_15784();

	protected abstract class_2680 method_15790(class_3610 state);

	public abstract boolean method_15793(class_3610 state);

	public abstract int method_15779(class_3610 state);

	public boolean method_15780(class_3611 fluid) {
		throw new AssertionError("stand-in");
	}

	protected void method_15775(class_2689.class_2690<class_3611, class_3610> builder) {
		throw new AssertionError("stand-in");
	}
}
