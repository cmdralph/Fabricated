package net.minecraft;

/**
 * {@code FlowingFluid}: {@code field_15900} is {@code LEVEL}; {@code method_15750} is {@code getFlowing()},
 * {@code method_15751} {@code getSource()}, {@code method_15737} {@code canConvertToSource(Level)}, {@code method_15730}
 * {@code beforeDestroyingBlock(LevelAccessor, BlockPos, BlockState)}, {@code method_15733}
 * {@code getSlopeFindDistance(LevelReader)}, {@code method_15739} {@code getDropOff(LevelReader)}, {@code method_15741}
 * {@code getLegacyLevel(FluidState)}.
 */
public abstract class class_3609 extends class_3611 {
	public static class_2758 field_15900;

	protected class_3609() {
	}

	public abstract class_3611 method_15750();

	public abstract class_3611 method_15751();

	protected abstract boolean method_15737(class_1937 level);

	protected abstract void method_15730(class_1936 level, class_2338 pos, class_2680 state);

	protected abstract int method_15733(class_4538 level);

	protected abstract int method_15739(class_4538 level);

	protected static int method_15741(class_3610 state) {
		throw new AssertionError("stand-in");
	}
}
