package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.class_1792;
import net.minecraft.class_1802;
import net.minecraft.class_1922;
import net.minecraft.class_1936;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2404;
import net.minecraft.class_2680;
import net.minecraft.class_2689;
import net.minecraft.class_3609;
import net.minecraft.class_3610;
import net.minecraft.class_3611;
import net.minecraft.class_4538;

/**
 * A Fabric mod's fluid, written the way Fabric API's own test fluids are, for {@link ForgeFluidTest}: it names no Forge
 * fluid type, as no Fabric fluid does. In Intermediary's names, as a Fabric mod for 1.20.1 is compiled (the stand-ins
 * name each method's Mojang name).
 */
abstract class ForgeTestFluid extends class_3609 {
	private final ForgeFluidTest.Family family;

	ForgeTestFluid(ForgeFluidTest.Family family) {
		this.family = family;
	}

	@Override
	public class_3611 method_15750() {
		return family.flowing;
	}

	@Override
	public class_3611 method_15751() {
		return family.source;
	}

	@Override
	public class_1792 method_15774() {
		return class_1802.field_8162;
	}

	@Override
	public boolean method_15737(class_1937 level) {
		return false;
	}

	@Override
	public void method_15730(class_1936 level, class_2338 pos, class_2680 state) {
	}

	@Override
	public int method_15733(class_4538 level) {
		return 4;
	}

	@Override
	public class_2680 method_15790(class_3610 state) {
		return family.block.method_9564().method_11657(class_2404.field_11278, method_15741(state));
	}

	@Override
	public boolean method_15780(class_3611 fluid) {
		return fluid == family.source || fluid == family.flowing;
	}

	@Override
	public int method_15739(class_4538 level) {
		return 1;
	}

	@Override
	public int method_15789(class_4538 level) {
		return 5;
	}

	@Override
	public boolean method_15777(class_3610 state, class_1922 level, class_2338 pos, class_3611 fluid, class_2350 direction) {
		return false;
	}

	@Override
	public float method_15784() {
		return 100.0F;
	}

	static final class Source extends ForgeTestFluid {
		Source(ForgeFluidTest.Family family) {
			super(family);
		}

		@Override
		public int method_15779(class_3610 state) {
			return 8;
		}

		@Override
		public boolean method_15793(class_3610 state) {
			return true;
		}
	}

	static final class Flowing extends ForgeTestFluid {
		Flowing(ForgeFluidTest.Family family) {
			super(family);
		}

		@Override
		public void method_15775(class_2689.class_2690<class_3611, class_3610> builder) {
			super.method_15775(builder);
			builder.method_11667(field_15900);
		}

		@Override
		public int method_15779(class_3610 state) {
			return state.method_11654(field_15900);
		}

		@Override
		public boolean method_15793(class_3610 state) {
			return false;
		}
	}
}
