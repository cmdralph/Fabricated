package io.github.cmdralph.fabricated.testmod;

import net.minecraft.resources.Identifier;

/**
 * What the test mod's {@code LootTableBuilderNamingMixin} adds to a loot table's builder: the name to give the table it
 * builds, as Porting Lib Loot names every table on Fabric, from its MODIFY listener and its builder mixin calling the
 * name setter NeoForge's table has too (CORPUS-90, {@code LootTest}).
 */
public interface LootNamedAsBuilt {
	void fabricatedTest$nameAsBuilt(Identifier name);
}
