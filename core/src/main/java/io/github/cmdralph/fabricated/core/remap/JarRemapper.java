package io.github.cmdralph.fabricated.core.remap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import io.github.cmdralph.fabricated.core.archive.ArchiveException;
import io.github.cmdralph.fabricated.core.archive.SafeArchive;
import io.github.cmdralph.fabricated.core.json.Json;
import io.github.cmdralph.fabricated.core.json.JsonException;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight;
import io.github.cmdralph.fabricated.core.mixin.Refmap;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.MethodRemapper;

/**
 * Produces the remapped contents of a Fabric jar. Pure bytecode processing: no class is ever loaded.
 *
 * <ul>
 *   <li>{@code .class} files are rewritten with {@link IntermediaryRemapper}; frames are copied, not
 *       recomputed, because renaming does not change stack shapes. A mixin injector's selector naming a method by its
 *       intermediary name alone gets the method's descriptor where its runtime name is shared ({@link SelectorPins}), and
 *       an accessor or invoker whose name Mixin would read an intermediary name from gets that name written out
 *       ({@link InferredTargets}). A member a library writes for another loader, which remapping makes the same member as its Fabric one, is left
 *       out ({@link #otherLoadersTwins}).</li>
 *   <li>Refmaps and service files have intermediary tokens rewritten.</li>
 *   <li>Signature files are removed (a remapped jar cannot keep valid signatures) and per-entry digest
 *       sections are dropped from the manifest.</li>
 *   <li>Everything else is copied unchanged.</li>
 * </ul>
 */
public final class JarRemapper {
	private static final Pattern INTERMEDIARY_METHOD = Pattern.compile("method_\\d+");
	private static final List<String> INJECTOR_PACKAGES = List.of("Lorg/spongepowered/asm/mixin/injection/", "Lcom/llamalad7/mixinextras/injector/");
	private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
	private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
	private static final Pattern INTERMEDIARY_MEMBER = Pattern.compile("(?:method|field|comp)_\\d+");

	private final MappingTable table;
	private final Supertypes supertypes;
	private final IntermediaryRemapper remapper;
	private final TokenRemapper tokens;

	public JarRemapper(MappingTable table) {
		this(table, Supertypes.NONE);
	}

	/**
	 * @param supertypes the classes outside a jar its classes may inherit from (the game's), and for
	 *                   {@link #remapClass(byte[], String)} the jar's own: a method Intermediary leaves under its official
	 *                   name, and the runtime renames, is renamed in a class that inherits it (CORPUS-86)
	 */
	public JarRemapper(MappingTable table, Supertypes supertypes) {
		this.table = table;
		this.supertypes = supertypes;
		this.remapper = new IntermediaryRemapper(table, supertypes);
		this.tokens = remapper.tokens();
	}

	/** This remapper, knowing {@code first}'s classes before its own. */
	public JarRemapper with(Supertypes first) {
		return new JarRemapper(table, first.or(supertypes));
	}

	/**
	 * Result of remapping one jar.
	 *
	 * @param leftOutMembers a note for each member left out as another loader's twin of a Fabric one ({@link #otherLoadersTwins})
	 */
	public record Result(Map<String, byte[]> files, List<String> strippedSignatureFiles, List<String> leftOutMembers) {
	}

	/**
	 * @param refmaps paths of refmap JSON files inside the jar (from the mixin configs)
	 */
	public Result remap(SafeArchive archive, Set<String> refmaps) throws ArchiveException, MappingException {
		Map<String, byte[]> out = new LinkedHashMap<>();
		List<String> stripped = new ArrayList<>();
		List<String> leftOut = new ArrayList<>();
		// Every entry is read once, as reading one again counts against the archive's limits again. Where the runtime renames
		// methods Intermediary leaves under their official names (SRG), the classes' supertypes are known before any class is
		// remapped, so an override of one in a class inheriting it from another of the jar's is renamed too (CORPUS-86).
		Map<String, byte[]> entries = new LinkedHashMap<>();
		Map<String, List<String>> classes = new HashMap<>();
		for (String name : archive.entryNames()) {
			if (archive.isDirectory(name)) continue;
			if (isSignatureFile(name)) {
				stripped.add(name);
				continue;
			}
			byte[] data = archive.read(name);
			if (data == null) continue;
			entries.put(name, data);
			if (table.hasPlainMembers() && name.endsWith(".class")) {
				try {
					ClassReader reader = new ClassReader(data);
					classes.putIfAbsent(reader.getClassName(), Supertypes.of(reader));
				} catch (RuntimeException e) {
					// remapping it reports the problem
				}
			}
		}
		IntermediaryRemapper names = classes.isEmpty() ? remapper : new IntermediaryRemapper(table, ((Supertypes) classes::get).or(supertypes));
		for (Iterator<Map.Entry<String, byte[]>> it = entries.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, byte[]> entry = it.next();
			it.remove(); // held once: the remapped copy replaces it
			String name = entry.getKey();
			byte[] data = entry.getValue();
			if (name.endsWith(".class")) {
				byte[] remapped = remapClass(data, name, leftOut, names);
				String newName = remapClassPath(name);
				out.put(newName, remapped);
			} else if (name.equals("META-INF/MANIFEST.MF")) {
				out.put(name, cleanManifest(data));
			} else if (name.startsWith("META-INF/services/")) {
				String service = name.substring("META-INF/services/".length());
				out.put("META-INF/services/" + tokens.remap(service), tokens.remap(new String(data, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
			} else if (refmaps.contains(name)) {
				out.put(name, remapRefmap(data, name));
			} else {
				out.put(name, data);
			}
		}
		return new Result(out, stripped, List.copyOf(leftOut));
	}

	public byte[] remapClass(byte[] data, String nameForErrors) throws MappingException {
		return remapClass(data, nameForErrors, new ArrayList<>(), remapper);
	}

	byte[] remapClass(byte[] data, String nameForErrors, List<String> leftOut) throws MappingException {
		return remapClass(data, nameForErrors, leftOut, remapper);
	}

	private byte[] remapClass(byte[] data, String nameForErrors, List<String> leftOut, IntermediaryRemapper names) throws MappingException {
		try {
			ClassReader reader = new ClassReader(data);
			ClassWriter writer = new ClassWriter(0);
			ClassVisitor remapping = new InferredTargets(new LambdaAwareRemapper(writer, names));
			Set<String> twins = otherLoadersTwins(reader, leftOut);
			if (!twins.isEmpty()) remapping = new LeavingOut(remapping, twins);
			reader.accept(table.hasOverloadedMethods() ? new SelectorPins(remapping, table) : remapping, 0);
			return writer.toByteArray();
		} catch (RuntimeException e) {
			throw new MappingException("Failed to remap " + nameForErrors + ": " + e, e);
		}
	}

	/**
	 * The members of a class (a method's name and descriptor, a field's name, {@code ':'} and descriptor, as the jar
	 * names them) that remapping makes the same member as another of the class, where that other names the classes as a
	 * Fabric jar does and these name a class by its runtime name, which no class has on Fabric (REMAP-4). Elementa,
	 * which Essential's Fabric build carries, gives each loader its own {@code width} for a text component, Fabric's
	 * taking {@code class_2561} and Forge's {@code Component}: remapped, the two are one method, and the class could not
	 * be defined with both ("Duplicate method name"), stopping the game as Essential started. Only another loader's
	 * code calls the twin, so it is left out and the Fabric member, which the jar's code reaches, is kept; members that
	 * meet otherwise are left as they are, with a note, as which to keep is not known.
	 */
	private Set<String> otherLoadersTwins(ClassReader reader, List<String> notes) {
		String owner = reader.getClassName();
		Map<String, List<String>> members = new LinkedHashMap<>();
		reader.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
				members.computeIfAbsent(remapper.mapFieldName(owner, name, descriptor) + ':' + remapper.mapDesc(descriptor), k -> new ArrayList<>(1))
						.add(name + ':' + descriptor);
				return null;
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				members.computeIfAbsent(remapper.mapMethodName(owner, name, descriptor) + remapper.mapMethodDesc(descriptor), k -> new ArrayList<>(1))
						.add(name + descriptor);
				return null;
			}
		}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		Set<String> twins = new HashSet<>();
		for (Map.Entry<String, List<String>> member : members.entrySet()) {
			List<String> same = member.getValue();
			if (same.size() < 2) continue;
			List<String> fabric = same.stream().filter(m -> !namesARuntimeOnlyClass(m)).toList();
			if (fabric.size() == 1) {
				for (String twin : same) {
					if (twin.equals(fabric.get(0))) continue;
					twins.add(twin);
					notes.add("Left out " + owner + "." + twin + ", written for another loader: remapped, it is the same member as the Fabric "
							+ fabric.get(0) + ", which is kept (REMAP-4)");
				}
			} else {
				notes.add("Remapped, " + same + " of " + owner + " are all " + member.getKey() + ", which a class can't hold twice; "
						+ "the class will fail to load (REMAP-4)");
			}
		}
		return twins;
	}

	/** Whether a member's descriptor names a class by a name only the runtime gives it, which on Fabric no class has. */
	private boolean namesARuntimeOnlyClass(String member) {
		int split = member.indexOf('(');
		if (split < 0) split = member.indexOf(':') + 1;
		String descriptor = member.substring(split);
		Type type = Type.getType(descriptor);
		List<Type> types = new ArrayList<>();
		if (type.getSort() == Type.METHOD) {
			types.addAll(List.of(type.getArgumentTypes()));
			types.add(type.getReturnType());
		} else {
			types.add(type);
		}
		for (Type t : types) {
			if (t.getSort() == Type.ARRAY) t = t.getElementType();
			if (t.getSort() != Type.OBJECT) continue;
			String name = t.getInternalName();
			if (table.mapClass(name).equals(name) && !table.unmapClass(name).equals(name)) return true;
		}
		return false;
	}

	/** Drops the members {@link #otherLoadersTwins} names, before they are remapped. */
	private static final class LeavingOut extends ClassVisitor {
		private final Set<String> members;

		LeavingOut(ClassVisitor next, Set<String> members) {
			super(Opcodes.ASM9, next);
			this.members = members;
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			return members.contains(name + ':' + descriptor) ? null : super.visitField(access, name, descriptor, signature, value);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			return members.contains(name + descriptor) ? null : super.visitMethod(access, name, descriptor, signature, exceptions);
		}
	}

	/**
	 * A class remapper that names a lambda's method by the functional interface it implements, which ASM's hands the
	 * remapper without the interface or the method's descriptor (PLAIN-1).
	 */
	private static final class LambdaAwareRemapper extends ClassRemapper {
		private final IntermediaryRemapper names;

		LambdaAwareRemapper(ClassVisitor next, IntermediaryRemapper names) {
			super(next, names);
			this.names = names;
		}

		@Override
		protected MethodVisitor createMethodRemapper(MethodVisitor methodVisitor) {
			return new MethodRemapper(api, methodVisitor, names) {
				@Override
				public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
					super.visitInvokeDynamicInsn(names.mapLambdaMethodName(name, descriptor, bootstrap, arguments), descriptor, bootstrap, arguments);
				}
			};
		}
	}

	/**
	 * Writes out the target of an {@code @Accessor} or {@code @Invoker} without one where Mixin would read an intermediary
	 * name from the method's ({@code getField_1655} for {@code field_1655}), before the class is remapped, which then
	 * remaps it as it remaps a target written out: Fabric Loom names an accessor so when the mod's jar has no refmap, and
	 * on the runtime's names no member has that name. Do a Barrel Roll's {@code KeyBindingAccessor.getField_1655}, for
	 * {@code KeyMapping.key}, failed pre-flight on NeoForge 21.1, its mixins all left out (ACC-4). Any other is left as
	 * it is.
	 */
	private static final class InferredTargets extends ClassVisitor {
		InferredTargets(ClassVisitor next) {
			super(Opcodes.ASM9, next);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
			if (method == null) return null;
			return new MethodVisitor(api, method) {
				@Override
				public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
					AnnotationVisitor values = super.visitAnnotation(annotation, visible);
					boolean accessor = annotation.equals(ACCESSOR);
					if (values == null || !accessor && !annotation.equals(INVOKER)) return values;
					String inferred = accessor ? MixinPreflight.inferAccessorName(name) : MixinPreflight.inferInvokerName(name);
					if (inferred == null || !INTERMEDIARY_MEMBER.matcher(inferred).matches()) return values;
					return new AnnotationVisitor(api, values) {
						private boolean written;

						@Override
						public void visit(String attribute, Object value) {
							if ("value".equals(attribute) && value instanceof String target) {
								// Mixin reads an empty target as none.
								if (target.isEmpty()) return;
								written = true;
							}
							super.visit(attribute, value);
						}

						@Override
						public void visitEnd() {
							if (!written) super.visit("value", inferred);
							super.visitEnd();
						}
					};
				}
			};
		}
	}

	/**
	 * Gives a mixin injector's selector that names a method by its intermediary name alone that method's descriptor, where
	 * its runtime name is one its class gives an overload too ({@link MappingTable#overloadedDescriptor}), before the
	 * class is remapped: Fabric Loom writes the name alone where it is unique, as LambDynamicLights'
	 * {@code @ModifyReturnValue(method = "method_23793")}, which picks {@code LevelRenderer.getLightColor} taking a block
	 * state on Fabric and, remapped to a bare {@code getLightColor}, the first of that name, its handler refused for the
	 * whole class (CORPUS-45). Anything else, an accessor's or invoker's name among them, is left as it is.
	 */
	private static final class SelectorPins extends ClassVisitor {
		private final MappingTable table;

		SelectorPins(ClassVisitor next, MappingTable table) {
			super(Opcodes.ASM9, next);
			this.table = table;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
			if (method == null) return null;
			return new MethodVisitor(api, method) {
				@Override
				public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
					AnnotationVisitor values = super.visitAnnotation(annotation, visible);
					if (values == null || INJECTOR_PACKAGES.stream().noneMatch(annotation::startsWith)) return values;
					return new AnnotationVisitor(api, values) {
						@Override
						public AnnotationVisitor visitArray(String attribute) {
							AnnotationVisitor selectors = super.visitArray(attribute);
							if (selectors == null || !"method".equals(attribute)) return selectors;
							return new AnnotationVisitor(api, selectors) {
								@Override
								public void visit(String unnamed, Object value) {
									super.visit(unnamed, value instanceof String selector ? pinned(selector) : value);
								}
							};
						}
					};
				}
			};
		}

		private String pinned(String selector) {
			if (!INTERMEDIARY_METHOD.matcher(selector).matches()) return selector;
			String desc = table.overloadedDescriptor(selector);
			return desc == null ? selector : selector + desc;
		}
	}

	private String remapClassPath(String name) {
		String internal = name.substring(0, name.length() - ".class".length());
		return remapper.map(internal) + ".class";
	}

	/**
	 * Rewrites every string of a refmap. Its keys are the selectors as the mod's annotations write them, which mostly
	 * name members by the mod's own names; one naming a member by its intermediary name ({@code method_1611(...)} with
	 * the mod's names in its descriptor, as Fabric API's hooks into lambdas do) is rewritten as the annotation is, or
	 * the annotation's rewritten selector found no entry and was read as it stood, descriptor and all (REMAP-3).
	 */
	public byte[] remapRefmap(byte[] data, String nameForErrors) throws MappingException {
		try {
			Object tree = Json.parse(new String(data, StandardCharsets.UTF_8), Refmap.MAX_LENGTH, Json.DEFAULT_MAX_DEPTH);
			return Json.write(remapJsonValues(tree), true).getBytes(StandardCharsets.UTF_8);
		} catch (JsonException e) {
			throw new MappingException("Refmap " + nameForErrors + " is not valid JSON: " + e.getMessage(), e);
		}
	}

	private Object remapJsonValues(Object node) {
		if (node instanceof String s) return tokens.remap(s);
		if (node instanceof Map<?, ?> map) {
			Map<String, Object> copy = new LinkedHashMap<>();
			for (Map.Entry<?, ?> e : map.entrySet()) copy.put(tokens.remap(String.valueOf(e.getKey())), remapJsonValues(e.getValue()));
			return copy;
		}
		if (node instanceof List<?> list) {
			List<Object> copy = new ArrayList<>(list.size());
			for (Object o : list) copy.add(remapJsonValues(o));
			return copy;
		}
		return node;
	}

	/** The jar's files unchanged, but for its signatures, which a copy with changed files can't keep either. */
	public static Map<String, byte[]> unsignedCopy(SafeArchive archive) throws ArchiveException {
		Map<String, byte[]> out = new LinkedHashMap<>();
		for (String name : archive.entryNames()) {
			if (archive.isDirectory(name) || isSignatureFile(name)) continue;
			byte[] data = archive.read(name);
			if (data == null) continue;
			out.put(name, name.equals("META-INF/MANIFEST.MF") ? cleanManifest(data) : data);
		}
		return out;
	}

	static boolean isSignatureFile(String name) {
		if (!name.startsWith("META-INF/") || name.indexOf('/', "META-INF/".length()) >= 0) return false;
		String upper = name.toUpperCase(Locale.ROOT);
		return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")
				|| upper.startsWith("META-INF/SIG-");
	}

	/** Keeps main attributes, drops per-entry sections (which carry now-invalid digests). */
	static byte[] cleanManifest(byte[] data) {
		try {
			Manifest original = new Manifest(new ByteArrayInputStream(data));
			Manifest clean = new Manifest();
			clean.getMainAttributes().putAll(original.getMainAttributes());
			if (clean.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION) == null) {
				clean.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			clean.write(out);
			return out.toByteArray();
		} catch (IOException e) {
			// A broken manifest is replaced by a minimal one rather than failing the whole mod.
			return "Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
		}
	}
}
