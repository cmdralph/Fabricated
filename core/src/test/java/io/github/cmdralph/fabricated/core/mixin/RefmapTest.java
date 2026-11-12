package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A selector as Mixin looks it up in a mod's refmap. */
class RefmapTest {
	private static final String MIXIN = "aztech/modern_industrialization/mixin/runtime_resources/PackConfigMixin";

	/**
	 * PRE-31: Mixin strips a selector's whitespace before it looks it up ({@code MemberInfo.parse}), and its annotation
	 * processor writes the key without it, so Modern Industrialization's target written with a space before its
	 * descriptor is mapped. Pre-flight looked it up as written, kept the development name, which Forge 47's game (SRG's
	 * names) hasn't, and left the hook out.
	 */
	@Test
	void aSelectorIsLookedUpWithoutItsWhitespace() throws Exception {
		Refmap refmap = Refmap.parse("{\"mappings\":{\"" + MIXIN + "\":{"
				+ "\"net/minecraft/server/packs/repository/PackRepository.openAllSelected()Ljava/util/List;\":"
				+ "\"Lnet/minecraft/server/packs/repository/PackRepository;m_10525_()Ljava/util/List;\"}}}");
		String mapped = "Lnet/minecraft/server/packs/repository/PackRepository;m_10525_()Ljava/util/List;";
		assertEquals(mapped, refmap.resolve(MIXIN, "net/minecraft/server/packs/repository/PackRepository.openAllSelected ()Ljava/util/List;"));
		assertEquals(mapped, refmap.resolve(MIXIN, " net/minecraft/server/packs/repository/PackRepository.openAllSelected\t(\n)Ljava/util/List; "));
		assertEquals(mapped, refmap.resolve(MIXIN, "net/minecraft/server/packs/repository/PackRepository.openAllSelected()Ljava/util/List;"));
		// One the refmap doesn't map comes back as written; another mixin's is not this one's.
		assertEquals("openAllSelected ()V", refmap.resolve(MIXIN, "openAllSelected ()V"));
		assertEquals("net/minecraft/server/packs/repository/PackRepository.openAllSelected ()Ljava/util/List;",
				refmap.resolve("com/example/Other", "net/minecraft/server/packs/repository/PackRepository.openAllSelected ()Ljava/util/List;"));
	}
}
