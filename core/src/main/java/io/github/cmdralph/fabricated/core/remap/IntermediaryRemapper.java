package io.github.cmdralph.fabricated.core.remap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;

/**
 * ASM {@link Remapper} from intermediary to runtime names.
 *
 * <p>Owners passed to the member hooks are the <em>original</em> (intermediary) names, which is what
 * the qualified fallback table is keyed by. Declarations in mod classes that override or shadow
 * Minecraft members (e.g. {@code @Shadow} fields named {@code field_N}) are renamed through the same
 * flat lookup, which keeps overrides and mixin shadows consistent. So are names with a prefix before an intermediary
 * name ({@code shadow$comp_925}): a {@code @Shadow} with a prefix, which Loom's static mixin remapping leaves as it is.
 */
public final class IntermediaryRemapper extends Remapper {
	/** A prefix, then an intermediary member name: not {@code lambda$method_1$0}, which is the mod's own. */
	private static final Pattern PREFIXED = Pattern.compile("[^$]+\\$(?:method|field|comp)_\\d+");

	private final MappingTable table;
	private final TokenRemapper tokens;
	private final Supertypes supertypes;
	/** By owner, its supertypes, transitively, nearest first, for the methods it inherits (CORPUS-86). */
	private final Map<String, List<String>> ancestors = new ConcurrentHashMap<>();

	/**
	 * @param supertypes the classes this remapper's owners may inherit from: a method Intermediary leaves under its official
	 *                   name, and the runtime renames, is renamed in an owner that inherits it from one of them (CORPUS-86)
	 */
	public IntermediaryRemapper(MappingTable table, Supertypes supertypes) {
		this.table = table;
		this.tokens = new TokenRemapper(table);
		this.supertypes = supertypes;
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
		if (!isIntermediaryMember(name)) {
			// A method Intermediary leaves under its official name, which the runtime renames (SRG, PLAIN-1).
			String plain = plainMethod(owner, name, descriptor);
			if (plain != null) return plain;
		}
		return mapMember(owner, name, descriptor);
	}

	/**
	 * The runtime name of a method Intermediary leaves under its official name and SRG renames, or null: the owner's own,
	 * else the one the nearest of its supertypes that declares it gives it (an override in a mod's class, a call naming a
	 * class that inherits the method), else as {@link MappingTable#mapPlainMethod}. Without the supertypes, an override
	 * whose descriptor names no Minecraft class kept the official name, which the runtime's callers don't call: Moonlight's
	 * {@code CaveFilter.Type.codec()}, overriding {@code PlacementModifierType.codec()} (SRG's {@code m_191869_}), stopped
	 * Forge 47's server as it loaded its data packs (CORPUS-86, AbstractMethodError).
	 */
	private String plainMethod(String owner, String name, String descriptor) {
		if (owner != null && table.isPlainMethod(name, descriptor) && table.ownPlainMethod(owner, name, descriptor) == null) {
			for (String ancestor : ancestors(owner)) {
				String inherited = table.ownPlainMethod(ancestor, name, descriptor);
				if (inherited != null) return inherited;
			}
		}
		return table.mapPlainMethod(owner, name, descriptor);
	}

	/** The owner's supertypes, transitively, breadth first: its superclass, then its interfaces, then theirs. */
	private List<String> ancestors(String owner) {
		List<String> known = ancestors.get(owner);
		if (known != null) return known;
		List<String> found = new ArrayList<>();
		Set<String> seen = new HashSet<>(Set.of(owner));
		Deque<String> queue = new ArrayDeque<>(List.of(owner));
		while (!queue.isEmpty()) {
			List<String> direct = supertypes.of(queue.poll());
			if (direct == null) continue;
			for (String type : direct) {
				if (seen.add(type)) {
					found.add(type);
					queue.add(type);
				}
			}
		}
		List<String> all = List.copyOf(found);
		ancestors.putIfAbsent(owner, all);
		return all;
	}

	@Override
	public String mapFieldName(String owner, String name, String descriptor) {
		return mapMember(owner, name, descriptor);
	}

	private String mapMember(String owner, String name, String descriptor) {
		if (isIntermediaryMember(name)) return table.mapMember(owner, name, descriptor);
		// Called for every member a class names, most of them the mod's own or the JDK's: only a name with a '$' after its
		// first character can be a prefixed intermediary name, so the pattern runs for those alone.
		if (name.indexOf('$') <= 0 || !PREFIXED.matcher(name).matches()) return name;
		int split = name.indexOf('$') + 1;
		return name.substring(0, split) + table.mapMember(owner, name.substring(split), descriptor);
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

	/**
	 * The name a lambda or method reference implements, from its {@code invokedynamic}: for {@code LambdaMetafactory}, a
	 * method of the functional interface it returns, which may be one Intermediary leaves under its official name and the
	 * runtime renames (PLAIN-1: Fabric API's {@code factory -> new FabricModResourcePack(...)} implements
	 * {@code Pack.ResourcesSupplier.open}, SRG's {@code m_247679_}); else as {@link #mapInvokeDynamicMethodName}.
	 */
	public String mapLambdaMethodName(String name, String descriptor, Handle bootstrap, Object[] arguments) {
		if (!isIntermediaryMember(name) && bootstrap.getOwner().equals("java/lang/invoke/LambdaMetafactory") && arguments.length > 0
				&& arguments[0] instanceof Type sam && sam.getSort() == Type.METHOD) {
			Type returned = Type.getReturnType(descriptor);
			if (returned.getSort() == Type.OBJECT) {
				String plain = plainMethod(returned.getInternalName(), name, sam.getDescriptor());
				if (plain != null) return plain;
			}
		}
		return name;
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
