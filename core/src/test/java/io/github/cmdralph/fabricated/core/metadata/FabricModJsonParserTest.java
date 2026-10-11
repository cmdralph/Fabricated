package io.github.cmdralph.fabricated.core.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.metadata.FabricModMetadata.EntrypointSpec;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;

class FabricModJsonParserTest {
	private static final String FULL = """
			{
			  "schemaVersion": 1,
			  "id": "example-mod",
			  "version": "1.2.3+mc1.21.11",
			  "name": "Example Mod",
			  "description": "Does things",
			  "authors": ["Alice", {"name": "Bob", "contact": {"email": "bob@example.com"}}],
			  "contributors": ["Carol"],
			  "contact": {"homepage": "https://example.com", "issues": "https://example.com/issues"},
			  "license": ["MIT", "CC0-1.0"],
			  "icon": {"16": "assets/example/icon16.png", "128": "assets/example/icon128.png"},
			  "environment": "*",
			  "provides": ["example_api"],
			  "entrypoints": {
			    "main": ["com.example.Main"],
			    "client": [{"adapter": "kotlin", "value": "com.example.Client"}],
			    "modmenu": ["com.example.ModMenu"]
			  },
			  "jars": [{"file": "META-INF/jars/lib.jar"}],
			  "mixins": ["example.mixins.json", {"config": "example.client.mixins.json", "environment": "client"}],
			  "accessWidener": "example.accesswidener",
			  "depends": {"fabricloader": ">=0.19.0", "minecraft": "~1.21.11", "fabric-api": "*", "cloth-config": [">=15", "14.0.x"]},
			  "recommends": {"modmenu": "*"},
			  "breaks": {"badmod": "<2"},
			  "languageAdapters": {"kotlin": "com.example.KotlinAdapter"},
			  "custom": {"modmenu": {"badges": ["library"]}, "fabric-loom:generated": false}
			}
			""";

	@Test
	void parsesEveryField() throws Exception {
		FabricModMetadata md = FabricModJsonParser.parse(FULL);
		assertEquals("example-mod", md.getId());
		assertEquals("1.2.3+mc1.21.11", md.getVersion().getFriendlyString());
		assertEquals("Example Mod", md.getName());
		assertEquals(List.of("example_api"), List.copyOf(md.getProvides()));
		assertEquals(2, md.getAuthors().size());
		assertEquals("bob@example.com", md.getAuthors().stream().toList().get(1).getContact().get("email").orElseThrow());
		assertEquals("https://example.com/issues", md.getContact().get("issues").orElseThrow());
		assertEquals(List.of("MIT", "CC0-1.0"), List.copyOf(md.getLicense()));
		assertEquals("assets/example/icon128.png", md.getIconPath(64).orElseThrow(), "smallest icon at least as large");
		assertEquals("assets/example/icon128.png", md.getIconPath(512).orElseThrow(), "largest icon as fallback");
		assertEquals(List.of(new EntrypointSpec("default", "com.example.Main")), md.entrypoints().get("main"));
		assertEquals(new EntrypointSpec("kotlin", "com.example.Client"), md.entrypoints().get("client").getFirst());
		assertEquals(List.of("META-INF/jars/lib.jar"), md.nestedJars());
		assertEquals(2, md.mixins().size());
		assertEquals(ModEnvironment.CLIENT, md.mixins().get(1).environment());
		assertEquals("example.accesswidener", md.classTweaker());
		assertEquals("com.example.KotlinAdapter", md.languageAdapters().get("kotlin"));
		assertEquals(CustomValue.CvType.OBJECT, md.getCustomValue("modmenu").getType());
		assertFalse(md.getCustomValue("fabric-loom:generated").getAsBoolean());

		ModDependency cloth = md.getDependencies().stream().filter(d -> d.getModId().equals("cloth-config")).findFirst().orElseThrow();
		assertEquals(ModDependency.Kind.DEPENDS, cloth.getKind());
		assertTrue(cloth.matches(Version.parse("15.1.0")));
		assertTrue(cloth.matches(Version.parse("14.0.9")));
		assertFalse(cloth.matches(Version.parse("14.1.0")));
		assertTrue(md.getDependencies().stream().anyMatch(d -> d.getKind() == ModDependency.Kind.BREAKS && d.getModId().equals("badmod")));
		assertTrue(md.getEnvironment().matches(EnvType.SERVER));
	}

	@Test
	void rejectsSchemaZeroAndInvalidIds() {
		MetadataException schema0 = assertThrows(MetadataException.class, () -> FabricModJsonParser.parse("{\"id\":\"old\",\"version\":\"1\"}"));
		assertTrue(schema0.getMessage().contains("schema version 0"));
		assertThrows(MetadataException.class, () -> FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"Bad Id\",\"version\":\"1\"}"));
		assertThrows(MetadataException.class, () -> FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"a\",\"version\":\"1\"}"), "too short");
		assertThrows(MetadataException.class, () -> FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"okid\"}"), "missing version");
		assertThrows(MetadataException.class, () -> FabricModJsonParser.parse("{\"schemaVersion\":2,\"id\":\"okid\",\"version\":\"1\"}"));
		assertThrows(MetadataException.class, () -> FabricModJsonParser.parse("not json"));
		assertThrows(MetadataException.class, () -> FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"okid\",\"version\":\"1\",\"environment\":\"moon\"}"));
	}

	@Test
	void defaultsOptionalFields() throws Exception {
		FabricModMetadata md = FabricModJsonParser.parse("{\"schemaVersion\":1,\"id\":\"minimal\",\"version\":\"0.1\",\"icon\":\"icon.png\"}");
		assertEquals("minimal", md.getName());
		assertEquals("", md.getDescription());
		assertEquals(ModEnvironment.UNIVERSAL, md.getEnvironment());
		assertTrue(md.entrypoints().isEmpty());
		assertEquals("icon.png", md.getIconPath(16).orElseThrow());
	}
}
