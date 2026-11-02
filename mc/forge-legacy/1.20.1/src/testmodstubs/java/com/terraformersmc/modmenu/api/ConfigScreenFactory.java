package com.terraformersmc.modmenu.api;

import net.minecraft.class_437;

/** {@code ConfigScreenFactory}, {@code class_437} being {@code Screen}. */
@FunctionalInterface
public interface ConfigScreenFactory<S extends class_437> {
	S create(class_437 parent);
}
