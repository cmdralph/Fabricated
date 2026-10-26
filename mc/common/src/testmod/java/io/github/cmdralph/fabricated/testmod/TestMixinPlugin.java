package io.github.cmdralph.fabricated.testmod;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * The test mod's mixin config plugin: checks that Fabric's start-up order held (its language adapter ran first), and
 * adds an entrypoint to Fabric Loader's storage as FrozenLib's plugin does ({@link AddedEntrypoint}). It loads no game
 * class (not even through {@code TestMod}): Mixin is still starting.
 */
public final class TestMixinPlugin implements IMixinConfigPlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestMixinPlugin.class);

	@Override
	public void onLoad(String mixinPackage) {
		boolean created = "created".equals(System.getProperty(EarlyAdapter.PROPERTY));
		LOGGER.info("FABRICATED-TEST early language adapter {}", created ? "OK" : "FAILED (not created before the mod's mixin plugin loaded)");
		AddedEntrypoint.add();
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		if (mixinClassName.endsWith(".RegistryDataLoaderMixin")) copyWorldRegistries(targetClass);
	}

	/**
	 * Replaces {@code RegistryDataLoader.WORLD_REGISTRIES} with a copy at the end of the class initializer, as a mod
	 * written for Fabric does to add registries of its own (Wover): registries added through Fabric's
	 * {@code DynamicRegistries} afterwards must still load (REG-4). Only where the class has that list.
	 */
	private static void copyWorldRegistries(ClassNode loader) {
		boolean hasList = false;
		for (FieldNode field : loader.fields) {
			hasList |= field.name.equals("WORLD_REGISTRIES") && field.desc.equals("Ljava/util/List;") && (field.access & Opcodes.ACC_STATIC) != 0;
		}
		if (!hasList) return;
		for (MethodNode method : loader.methods) {
			if (!method.name.equals("<clinit>")) continue;
			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (insn.getOpcode() != Opcodes.RETURN) continue;
				InsnList copy = new InsnList();
				copy.add(new TypeInsnNode(Opcodes.NEW, "java/util/ArrayList"));
				copy.add(new InsnNode(Opcodes.DUP));
				copy.add(new FieldInsnNode(Opcodes.GETSTATIC, loader.name, "WORLD_REGISTRIES", "Ljava/util/List;"));
				copy.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V", false));
				copy.add(new FieldInsnNode(Opcodes.PUTSTATIC, loader.name, "WORLD_REGISTRIES", "Ljava/util/List;"));
				method.instructions.insertBefore(insn, copy);
			}
			method.maxStack += 3;
			LOGGER.info("FABRICATED-TEST RegistryDataLoader.WORLD_REGISTRIES replaced with a copy, as Wover does");
		}
	}
}
