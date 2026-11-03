package io.github.cmdralph.fabricated.forgetest;

import net.minecraft.resources.Identifier;

/**
 * What the test mod's {@code LootTableBuilderNamingMixin} adds to a loot table's builder: the name to give the table it
 * builds, as Porting Lib Loot names every table, from its MODIFY listener and its builder mixin calling the name setter
 * Forge's table has too (CORPUS-90, {@link ForgeLootTest}).
 */
public interface LootNamedAsBuilt {
	void fabricatedForgeTest$nameAsBuilt(Identifier name);
}
