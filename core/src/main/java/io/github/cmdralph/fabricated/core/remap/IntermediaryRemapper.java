package io.github.cmdralph.fabricated.core.remap;

import org.objectweb.asm.commons.Remapper;

/**
 * ASM {@link Remapper} from intermediary to runtime names.
 *
 * <p>Owners passed to the member hooks are the <em>original</em> (intermediary) names, which is what
 * the qualified fallback table is keyed by. Declarations in mod classes that override or shadow
 * Minecraft members (e.g. {@code @Shadow} fields named {@code field_N}) are renamed through the same
 * flat lookup, which keeps overrides and mixin shadows consistent.
 */
public final class IntermediaryRemapper extends Remapper {
	private final MappingTable table;
	private final TokenRemapper tokens;

	public IntermediaryRemapper(MappingTable table) {
		this.table = table;
		this.tokens = new TokenRemapper(table);
	}

	/** Rewrites intermediary names inside text (refmaps, service files, string constants) with the same table. */
	public TokenRemapper tokens() {
		return tokens;
	}

	@Override
	public String map(String internalName) {
		return table.mapClass(internalName);
	}

	@Override
	public String mapMethodName(String owner, String name, String descriptor) {
		if (!isIntermediaryMember(name)) return name;
		return table.mapMember(owner, name, descriptor);
	}

	@Override
	public String mapFieldName(String owner, String name, String descriptor) {
		if (!isIntermediaryMember(name)) return name;
		return table.mapMember(owner, name, descriptor);
	}

	@Override
	public String mapRecordComponentName(String owner, String name, String descriptor) {
		return mapFieldName(owner, name, descriptor);
	}

	@Override
	public String mapInvokeDynamicMethodName(String name, String descriptor) {
		if (!isIntermediaryMember(name)) return name;
		String flat = table.mapMemberFlat(name);
		return flat != null ? flat : name;
	}

	/** String constants and annotation strings: rewrite intermediary tokens only. */
	@Override
	public Object mapValue(Object value) {
		if (value instanceof String s) return tokens.remap(s);
		return super.mapValue(value);
	}

	static boolean isIntermediaryMember(String name) {
		return name.startsWith("method_") || name.startsWith("field_") || name.startsWith("comp_");
	}
}
