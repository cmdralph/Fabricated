package net.fabricmc.fabric.api.client.model.loading.v1;

import net.minecraft.class_1087;
import net.minecraft.class_1100;
import net.minecraft.class_2960;

public final class ModelModifier {
	private ModelModifier() {
	}

	@FunctionalInterface
	public interface BeforeBake {
		class_1100 modifyModelBeforeBake(class_1100 model, Context context);

		interface Context {
			class_2960 id();
		}
	}

	@FunctionalInterface
	public interface AfterBake {
		class_1087 modifyModelAfterBake(class_1087 model, Context context);

		interface Context {
			class_2960 id();
		}
	}
}
