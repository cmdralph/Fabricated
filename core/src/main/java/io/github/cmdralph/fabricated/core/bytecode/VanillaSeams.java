package io.github.cmdralph.fabricated.core.bytecode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Vanilla's methods a platform took out of a game class, asking its own extension methods where vanilla called them, put
 * back so that Fabric mods' hooks of them apply (SEAM-1); and vanilla's calls a platform makes through its block state's
 * or its item stack's extension or a hook of its own, put back where the platform's code then does what vanilla's does
 * (SEAM-3's block breaking, SEAM-4's harvest checks, SEAM-6's item checks, SEAM-8's friction, sound type, piglin, sweep and
 * enchantment level calls, {@link VanillaCalls}, the table's second
 * shape). Each {@link Seam} is a row: the
 * vanilla method, and the
 * platform's code at the places vanilla calls it, calls of the method's parameters joined by {@code &&}. Where a class
 * lacks the method and has that code, the method is declared again with the platform's code as its body, and each place
 * calls it instead, so every check answers as the platform's code did. A method the class still declares is left alone
 * (NeoForge 26.2 and 26.3 keep the stickiness ones, their bodies asking the extensions).
 *
 * <p>Piston stickiness: vanilla's {@code PistonStructureResolver} asks its private static {@code isSticky(state)} and
 * {@code canStickToEachOther(state1, state2)}; NeoForge 21.1, 21.11 and 26.1.2 and Forge 47 to 66 ask
 * {@code state.isStickyBlock()} and {@code state1.canStickTo(state2) && state2.canStickTo(state1)} there instead (read in
 * their classes). NeoForge 26.1.2's {@code addBlockLine} asks {@code state1.canStickTo(state1)} for its second check, which
 * its other check and NeoForge 26.2 ask the other way: that form is restored as vanilla's call too, so a class rewritten
 * (one a Fabric mod's mixin hooks a seam of) asks both ways there, as NeoForge 26.2 does; elsewhere it asks as NeoForge
 * 26.1.2 does.
 *
 * <p>The same rewrite gives pre-flight its view of the game ({@link #over}) and the game its classes at load (before
 * Mixin, {@link #restoreWhole}), so a hook pre-flight passes finds what it hooks; at load, only the classes in which a
 * Fabric mod's mixin member names or shadows a seam are rewritten (Lithium's mixin of the resolver, which hooks none,
 * leaves it as the platform has it). A rewritten class no longer makes the platform's calls where vanilla
 * called the seam, so a native mod's mixin hooking them there finds none. Each change keeps
 * the stack as it was at every instruction: a call takes the operands of the platform's first call and leaves its
 * result, and the platform's later calls, which only loaded locals, go.
 */
public final class VanillaSeams {
	/**
	 * The invisible annotation on a method declared again. The JVM and Mixin never read it, and no class of that name exists:
	 * pre-flight reads it to tell the hooks of such methods ({@link #restoredIn}).
	 */
	public static final String MARKER = "Lio/github/cmdralph/fabricated/core/bytecode/RestoredSeam;";
	private static final String BLOCK_STATE = "net/minecraft/world/level/block/state/BlockState";
	private static final String RESOLVER = "net/minecraft/world/level/block/piston/PistonStructureResolver";
	/** A method's name where the game runs with SRG's member names (Forge 47): {@code m_<number>_}. */
	private static final Pattern SRG_METHOD = Pattern.compile("m_\\d+_");

	/**
	 * A call the platform makes where vanilla calls a seam: an instance method, its receiver and arguments given by the
	 * indexes of the seam's parameters.
	 */
	record Call(String owner, String name, String desc, int... operands) {
	}

	/**
	 * A vanilla method a platform took out.
	 *
	 * @param owner   the class, by internal name
	 * @param name    its name, in Mojang's names
	 * @param srgName its name in SRG's, where the game runs with them (Forge 47)
	 * @param desc    its descriptor (static, returning a boolean)
	 * @param forms   the platform's code where vanilla calls it, each the calls joined by {@code &&}: the first is its body
	 */
	record Seam(String owner, String name, String srgName, String desc, List<List<Call>> forms) {
		/** The platform's answer, as a report says it: {@code a.canStickTo(b) && b.canStickTo(a)}. */
		String answer() {
			return forms.get(0).stream().map(c -> (char) ('a' + c.operands()[0]) + "." + c.name() + "("
					+ Arrays.stream(c.operands()).skip(1).mapToObj(o -> String.valueOf((char) ('a' + o))).collect(Collectors.joining(", ")) + ")")
					.collect(Collectors.joining(" && "));
		}
	}

	private static Call blockState(String name, String desc, int... operands) {
		return new Call(BLOCK_STATE, name, desc, operands);
	}

	private static final String STATE = "L" + BLOCK_STATE + ";";
	/** The table, a row per vanilla method. */
	static final List<Seam> SEAMS = List.of(
			new Seam(RESOLVER, "isSticky", "m_155937_", "(" + STATE + ")Z", List.of(List.of(blockState("isStickyBlock", "()Z", 0)))),
			new Seam(RESOLVER, "canStickToEachOther", "m_155939_", "(" + STATE + STATE + ")Z", List.of(
					List.of(blockState("canStickTo", "(" + STATE + ")Z", 0, 1), blockState("canStickTo", "(" + STATE + ")Z", 1, 0)),
					// NeoForge 26.1.2's addBlockLine.
					List.of(blockState("canStickTo", "(" + STATE + ")Z", 0, 1), blockState("canStickTo", "(" + STATE + ")Z", 0, 0)))));

	/** The classes the table names, by internal name. */
	public static final Set<String> CLASSES = Stream.concat(SEAMS.stream().map(Seam::owner), VanillaCalls.CLASSES.stream())
			.collect(Collectors.toUnmodifiableSet());

	private VanillaSeams() {
	}

	/** Whether what the table puts back in the class (internal name) is vanilla's calls (SEAM-3, SEAM-4, SEAM-6, SEAM-8), not its methods. */
	public static boolean putsBackCalls(String internalName) {
		return VanillaCalls.CLASSES.contains(internalName);
	}

	/**
	 * The findings what the table puts back in the class answers, as reports name them: {@code SEAM-1} for vanilla's
	 * methods, and for vanilla's calls those of its rows ({@code SEAM-3, SEAM-4} for the server's game mode).
	 */
	public static String findings(String internalName) {
		return putsBackCalls(internalName) ? VanillaCalls.findings(internalName) : "SEAM-1";
	}

	/**
	 * Why the class's platform makes vanilla's call {@code owner;name} in its method otherwise, where Fabricated doesn't put
	 * the call back, as pre-flight's hint for a hook of it says it, its finding named (SEAM-3: Forge's server's
	 * {@code playerWillDestroy}; SEAM-4: the held item's {@code canAttackBlock} or {@code canDestroyBlock}, which the
	 * platforms ask inside their block break event; SEAM-6: a mob's shears check, which the platforms leave to the shears
	 * item, and the client's map and crossbow checks, which they ask of the item's class); null for any other.
	 */
	public static String callNotPutBack(ClassNode target, String method, String desc, String owner, String name) {
		return VanillaCalls.notPutBack(target, method, desc, owner, name);
	}

	/**
	 * In how many places of the method, as read, vanilla's call {@code owner;name(desc)} is put back (SEAM-3, SEAM-4, SEAM-6, SEAM-8): each
	 * of the platform's calls it stands in for, where vanilla's code makes it once; 0 where it isn't.
	 */
	public static int putBackPlaces(MethodNode method, String owner, String name, String desc) {
		return VanillaCalls.places(method, owner, name, desc);
	}

	/**
	 * The classes whose methods' calls putting the class's seams back may change ({@link Rewrite#others}, of any form the
	 * table reads), and the class itself: only a mixin naming one of them can hook what the rewrite moves.
	 */
	static Set<String> concerned(String internalName) {
		Set<String> concerned = new HashSet<>(Set.of(internalName));
		if (putsBackCalls(internalName)) concerned.addAll(VanillaCalls.otherClasses(internalName));
		return concerned;
	}

	/**
	 * The methods of other classes whose calls putting the class's seams back may change, by class, as names, of any form
	 * the table reads ({@link Rewrite#others} of each): what a rewrite of the class as it is changes is among them.
	 */
	static Map<String, Set<String>> mayChange(String internalName) {
		return putsBackCalls(internalName) ? VanillaCalls.otherMethods(internalName) : Map.of();
	}

	/** Whether the table names the class (internal name): a class whose seams {@link #restore} may put back. */
	public static boolean names(String internalName) {
		return CLASSES.contains(internalName);
	}

	/**
	 * Puts back the class's seams it lacks and has the platform's code for, each marked with {@link #MARKER}. Returns the
	 * methods declared (name and descriptor); none when the class is unchanged.
	 */
	public static List<String> restore(ClassNode node) {
		List<String> restored = new ArrayList<>();
		if (!names(node.name)) return restored;
		boolean srg = srg(node);
		// The class's own methods: a seam's body makes the platform's calls.
		List<MethodNode> own = List.copyOf(node.methods);
		for (Seam seam : SEAMS) {
			if (!seam.owner().equals(node.name)) continue;
			String name = srg ? seam.srgName() : seam.name();
			if (node.methods.stream().anyMatch(m -> m.desc.equals(seam.desc()) && (m.name.equals(seam.name()) || m.name.equals(seam.srgName())))) continue;
			int calls = 0;
			for (MethodNode method : own) calls += pointAt(method, seam, name);
			if (calls == 0) continue;
			node.methods.add(body(seam, name));
			restored.add(name + seam.desc());
		}
		restored.addAll(VanillaCalls.restore(node));
		return restored;
	}

	/** Whether the class's methods have SRG's names, as where the game runs with them (Forge 47): {@code m_<number>_}. */
	static boolean srg(ClassNode node) {
		return node.methods.stream().anyMatch(m -> SRG_METHOD.matcher(m.name).matches());
	}

	/**
	 * What {@link #restoreWhole} did: the seams put back, or the failure that left the class as it was.
	 *
	 * @param restored as {@link #restore} returns them; empty on a failure
	 * @param failure  what {@link #restore} threw, or null
	 */
	public record Restoring(List<String> restored, Throwable failure) {
	}

	/**
	 * {@link #restore}, all or nothing, as a class loads: on a copy of the class, whose methods and fields then replace the
	 * class's. Where it throws, the class stays exactly as it was, and the failure is returned for the caller to log, never
	 * thrown: the class loads as the platform has it, its Fabric mods' hooks of the seams finding nothing, as before SEAM-1.
	 */
	public static Restoring restoreWhole(ClassNode node) {
		try {
			ClassNode copy = new ClassNode();
			node.accept(copy);
			List<String> restored = restore(copy);
			if (!restored.isEmpty()) {
				node.methods.clear();
				node.methods.addAll(copy.methods);
				node.fields.clear();
				node.fields.addAll(copy.fields);
			}
			return new Restoring(restored, null);
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			return new Restoring(List.of(), e);
		}
	}

	/**
	 * What putting a class's seams back would change, read on a copy of the class as the platform has it.
	 *
	 * @param seams      the seams put back, as {@link #restoredIn} names them
	 * @param movedCalls the platform's calls that move into them, or that vanilla's calls put back stand in for, as
	 *                   {@code owner;name(desc)}
	 * @param holders    the class's methods holding those calls, as {@code name(desc)}
	 * @param bypassed   the class's methods no longer reached where vanilla's calls are made instead (SEAM-3), as
	 *                   {@code name(desc)}
	 * @param added      the members the rewrite names anew in the class's methods ({@code owner;name(desc)},
	 *                   {@code owner;name:desc})
	 * @param others     the game's other methods whose calls change where vanilla's calls are made instead, by class, as
	 *                   names (Mojang's and SRG's): the extensions' defaults and the platform's hooks, no longer called, on
	 *                   Forge's server {@code Level.removeBlock}, called where Forge's default sets the block itself, and on
	 *                   Forge {@code Player.hasCorrectToolForDrops}, called where Forge's default only fires its event
	 * @param constants  whether the holders get constants they didn't have (vanilla's calls' {@code false} and {@code 11},
	 *                   SEAM-3), which a hook of a constant there may count
	 */
	public record Rewrite(List<Restored> seams, Set<String> movedCalls, Set<String> holders, Set<String> bypassed, Set<String> added,
			Map<String, Set<String>> others, boolean constants) {
		static final Rewrite NONE = new Rewrite(List.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of(), false);

		public boolean isEmpty() {
			return seams.isEmpty();
		}
	}

	/** {@link Rewrite What putting the class's seams back would change}; nothing where none would be put back. */
	public static Rewrite rewriteOf(ClassNode platformClass) {
		if (!names(platformClass.name)) return Rewrite.NONE;
		ClassNode copy = new ClassNode();
		platformClass.accept(copy);
		try {
			if (restore(copy).isEmpty()) return Rewrite.NONE;
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			return Rewrite.NONE; // nothing would be put back as it loads (restoreWhole)
		}
		List<Restored> seams = restoredIn(copy);
		Set<String> put = new HashSet<>();
		for (Restored seam : seams) if (seam.in() == null) put.add(seam.name() + seam.desc());
		Set<String> holders = new TreeSet<>();
		for (MethodNode method : copy.methods) {
			if (put.contains(method.name + method.desc) || method.instructions == null) continue;
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof MethodInsnNode call && call.owner.equals(copy.name) && put.contains(call.name + call.desc)) {
					holders.add(method.name + method.desc);
					break;
				}
			}
		}
		Set<String> moved = new TreeSet<>();
		for (Seam seam : SEAMS) {
			if (!seam.owner().equals(copy.name) || seams.stream().noneMatch(r -> r.in() == null && r.desc().equals(seam.desc())
					&& (r.name().equals(seam.name()) || r.name().equals(seam.srgName())))) continue;
			for (List<Call> form : seam.forms()) {
				for (Call call : form) moved.add(call.owner() + ";" + call.name() + call.desc());
			}
		}
		Set<String> bypassed = new TreeSet<>();
		Map<String, Set<String>> others = new TreeMap<>();
		VanillaCalls.changes(platformClass, copy, moved, holders, bypassed, others);
		return new Rewrite(List.copyOf(seams), Collections.unmodifiableSet(moved), Collections.unmodifiableSet(holders), Collections.unmodifiableSet(bypassed),
				Collections.unmodifiableSet(VanillaCalls.added(platformClass, copy)), Collections.unmodifiableMap(others),
				VanillaCalls.addsConstants(platformClass, copy));
	}

	/** The game's classes with their seams put back ({@link #restore}): pre-flight's view, as they will load. */
	public static ClassBytesSource over(ClassBytesSource game) {
		return over(game, Set.of());
	}

	/**
	 * As {@link #over(ClassBytesSource)}, but for the classes {@code kept} (internal names), left as the platform has them,
	 * as they then load: a native mod's mixin hooks the platform's code their rewrite would move ({@link SeamConflicts}).
	 * Where the rewrite throws, the class is read as the platform has it, as {@link #restoreWhole} then loads it.
	 */
	public static ClassBytesSource over(ClassBytesSource game, Set<String> kept) {
		return over(game, kept, (internalName, failure) -> {
		});
	}

	/**
	 * As {@link #over(ClassBytesSource, Set)}, telling {@code failed} of each class whose rewrite throws, and what it threw.
	 * A class is rewritten once, at its first read, and read so after: pre-flight reads a class as often as the mods' mixins
	 * target it, and the game's classes don't change. The bytes are what is kept, each read parsing its own tree. The
	 * classes whose members the rewrites name are read once for the view, whichever class names them.
	 */
	public static ClassBytesSource over(ClassBytesSource game, Set<String> kept, BiConsumer<String, Throwable> failed) {
		Map<String, byte[]> read = new ConcurrentHashMap<>();
		ClassIndex members = new ClassIndex(game);
		return internalName -> {
			if (!names(internalName) || kept.contains(internalName)) return game.read(internalName);
			byte[] done = read.get(internalName);
			if (done != null) return done;
			byte[] data = game.read(internalName);
			if (data == null) return null;
			done = rewritten(internalName, data, game, members, failed);
			byte[] first = read.putIfAbsent(internalName, done);
			return first != null ? first : done;
		};
	}

	/** The class's bytes with its seams put back, or as they are where nothing is or the rewrite throws ({@link #over}). */
	private static byte[] rewritten(String internalName, byte[] data, ClassBytesSource game, ClassIndex members, BiConsumer<String, Throwable> failed) {
		// A class whose bytes name none of the platform's calls its rows put back in (Player where no shield action is asked)
		// isn't parsed: nothing goes back there.
		if (!VanillaCalls.mayApply(internalName, data)) return data;
		try {
			if (VanillaCalls.receiversOnly(internalName)) return rewrittenLight(data, game, members);
			ClassNode node = new ClassNode();
			new ClassReader(data).accept(node, 0);
			if (restore(node).isEmpty()) return data;
			// Nothing is put back that names what the game lacks: the class then loads, and pre-flight reads it, as it is.
			ClassNode platform = new ClassNode();
			new ClassReader(data).accept(platform, 0);
			if (!VanillaCalls.resolves(VanillaCalls.added(platform, node), members, VanillaCalls.copied(platform, game))) return data;
			ClassWriter writer = new ClassWriter(0);
			node.accept(writer);
			return writer.toByteArray();
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			failed.accept(internalName, e);
			return data;
		}
	}

	/**
	 * {@link #rewritten} of a class whose rows are all extensions' calls of their objects (SEAM-8: LivingEntity, Entity,
	 * BlockItem...), large classes most mods' mixins target: only its methods of the rows' names are read whole, the others
	 * as their names alone, which is all the rows ask of them (no platform's package is looked for in their code); the
	 * class is written with its other methods copied as they are, the rewritten ones in their places and the helpers after,
	 * as {@link #restoreWhole} leaves the class. The class's methods aren't changed in place, so the copy kept of them
	 * before is the class as the platform has it.
	 */
	private static byte[] rewrittenLight(byte[] data, ClassBytesSource game, ClassIndex members) {
		ClassReader reader = new ClassReader(data);
		Set<String> names = VanillaCalls.methodNames(reader.getClassName());
		ClassNode node = new ClassNode();
		reader.accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9, node) {
			@Override
			public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				if (names.contains(name)) return super.visitMethod(access, name, descriptor, signature, exceptions);
				node.methods.add(new MethodNode(access, name, descriptor, signature, exceptions));
				return null;
			}
		}, 0);
		ClassNode platform = new ClassNode();
		platform.version = node.version;
		platform.access = node.access;
		platform.name = node.name;
		platform.superName = node.superName;
		platform.interfaces = new ArrayList<>(node.interfaces);
		platform.methods = new ArrayList<>(node.methods);
		int fields = node.fields.size();
		if (restore(node).isEmpty()) return data;
		if (!VanillaCalls.resolves(VanillaCalls.added(platform, node), members, VanillaCalls.copied(platform, game))) return data;
		Map<String, MethodNode> replaced = new java.util.HashMap<>();
		List<MethodNode> appended = new ArrayList<>();
		for (int i = 0; i < node.methods.size(); i++) {
			MethodNode method = node.methods.get(i);
			if (i >= platform.methods.size()) appended.add(method);
			else if (method != platform.methods.get(i)) replaced.put(method.name + method.desc, method);
		}
		List<org.objectweb.asm.tree.FieldNode> added = node.fields.subList(fields, node.fields.size());
		ClassWriter writer = new ClassWriter(reader, 0);
		reader.accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9, writer) {
			@Override
			public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				MethodNode method = replaced.get(name + descriptor);
				if (method == null) return super.visitMethod(access, name, descriptor, signature, exceptions);
				method.accept(writer);
				return null;
			}

			@Override
			public void visitEnd() {
				for (org.objectweb.asm.tree.FieldNode field : added) field.accept(writer);
				for (MethodNode method : appended) method.accept(writer);
				super.visitEnd();
			}
		}, 0);
		return writer.toByteArray();
	}

	/**
	 * The seams a class as read has had put back, as {@link #restore} marked them: its methods declared again, with the
	 * platform's answer they give ({@code isSticky(L…;)Z} to {@code a.isStickyBlock()}), and vanilla's calls put back in
	 * its methods (SEAM-3), with the extension's default they stand for.
	 */
	public static List<Restored> restoredIn(ClassNode node) {
		List<Restored> found = new ArrayList<>();
		for (MethodNode method : node.methods) {
			if (method.invisibleAnnotations == null || method.invisibleAnnotations.stream().noneMatch(a -> MARKER.equals(a.desc))) continue;
			for (Seam seam : SEAMS) {
				if (seam.owner().equals(node.name) && seam.desc().equals(method.desc) && (seam.name().equals(method.name) || seam.srgName().equals(method.name))) {
					found.add(new Restored(node.name, method.name, method.desc, null, seam.answer()));
				}
			}
		}
		found.addAll(VanillaCalls.restoredIn(node));
		return found;
	}

	/**
	 * A seam a class has had put back, as the game names it: a method of its own ({@code in} null, SEAM-1), with the
	 * platform's answer it gives; or vanilla's call put back in its method {@code in} ({@code name(desc)}, SEAM-3, SEAM-4),
	 * with the extension's default or the platform's hook ({@code Interface.method}) the call stands for.
	 *
	 * @param finding the finding it answers
	 * @param through how the platform makes vanilla's call, as pre-flight's note says it ({@code the block's
	 *                IBlockExtension.onDestroyedByPlayer}); null for a method
	 * @param note    what putting it back does, as the note says it; null for a method
	 */
	public record Restored(String owner, String name, String desc, String in, String answer, String finding, String through, String note) {
		/** A method put back (SEAM-1). */
		public Restored(String owner, String name, String desc, String in, String answer) {
			this(owner, name, desc, in, answer, "SEAM-1", null, null);
		}
	}

	/** Points the method's places with one of the seam's forms at the seam; how many. */
	private static int pointAt(MethodNode method, Seam seam, String name) {
		if (method.instructions == null || method.instructions.size() == 0) return 0;
		Set<LabelNode> targets = jumpTargets(method);
		int pointed = 0;
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (!(insn instanceof MethodInsnNode call)) continue;
			for (List<Call> form : seam.forms()) {
				if (!matches(call, form.get(0))) continue;
				List<AbstractInsnNode> rest = form.size() == 1 ? List.of() : rest(call, form, targets);
				if (rest == null) continue;
				for (AbstractInsnNode gone : rest) method.instructions.remove(gone);
				MethodInsnNode seamCall = new MethodInsnNode(Opcodes.INVOKESTATIC, seam.owner(), name, seam.desc(), false);
				method.instructions.set(call, seamCall);
				insn = seamCall;
				pointed++;
				break;
			}
		}
		return pointed;
	}

	private static boolean matches(MethodInsnNode insn, Call call) {
		return (insn.getOpcode() == Opcodes.INVOKEVIRTUAL || insn.getOpcode() == Opcodes.INVOKEINTERFACE) && insn.owner.equals(call.owner())
				&& insn.name.equals(call.name()) && insn.desc.equals(call.desc());
	}

	/**
	 * The instructions after {@code first} that make the form's other calls, ending with the jump after its last, where the
	 * code is the form: {@code first} takes the seam's parameters loaded from locals, in order, and each call is followed by
	 * a jump if false to one place, its operands the locals the first call's parameters came from; no instruction between
	 * the first jump and the last is a jump's target. Null where it isn't.
	 */
	private static List<AbstractInsnNode> rest(MethodInsnNode first, List<Call> form, Set<LabelNode> targets) {
		int[] firstOperands = form.get(0).operands();
		int[] locals = new int[firstOperands.length];
		AbstractInsnNode at = first;
		for (int i = firstOperands.length - 1; i >= 0; i--) {
			at = previousReal(at);
			if (firstOperands[i] != i || !(at instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD) return null;
			locals[i] = load.var;
		}
		AbstractInsnNode jump = nextReal(first);
		if (!(jump instanceof JumpInsnNode ifFalse) || ifFalse.getOpcode() != Opcodes.IFEQ) return null;
		List<AbstractInsnNode> rest = new ArrayList<>();
		at = jump;
		for (Call call : form.subList(1, form.size())) {
			for (int operand : call.operands()) {
				at = nextReal(at);
				if (!(at instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD || load.var != locals[operand]) return null;
				rest.add(at);
			}
			at = nextReal(at);
			if (!(at instanceof MethodInsnNode next) || !matches(next, call)) return null;
			rest.add(at);
			at = nextReal(at);
			if (!(at instanceof JumpInsnNode alsoFalse) || alsoFalse.getOpcode() != Opcodes.IFEQ || alsoFalse.label != ifFalse.label) return null;
			rest.add(at);
		}
		for (AbstractInsnNode insn = jump.getNext(); insn != at; insn = insn.getNext()) {
			if (insn instanceof LabelNode label && targets.contains(label)) return null;
		}
		return rest;
	}

	private static AbstractInsnNode previousReal(AbstractInsnNode insn) {
		do insn = insn.getPrevious(); while (insn != null && insn.getOpcode() < 0);
		return insn;
	}

	private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
		do insn = insn.getNext(); while (insn != null && insn.getOpcode() < 0);
		return insn;
	}

	/** The labels a jump, a switch or an exception handler goes to. */
	static Set<LabelNode> jumpTargets(MethodNode method) {
		Set<LabelNode> targets = new HashSet<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof JumpInsnNode jump) targets.add(jump.label);
			if (insn instanceof TableSwitchInsnNode table) {
				targets.add(table.dflt);
				targets.addAll(table.labels);
			}
			if (insn instanceof LookupSwitchInsnNode lookup) {
				targets.add(lookup.dflt);
				targets.addAll(lookup.labels);
			}
		}
		for (TryCatchBlockNode block : method.tryCatchBlocks) targets.add(block.handler);
		return targets;
	}

	/**
	 * {@code private static boolean name(params) { return call && call...; }}: the seam's first form, with its maxs and the
	 * one frame its jumps need (the method's start, so {@code F_SAME}).
	 */
	private static MethodNode body(Seam seam, String name) {
		MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name, seam.desc(), null, null);
		method.invisibleAnnotations = new ArrayList<>(List.of(new AnnotationNode(MARKER)));
		List<Call> form = seam.forms().get(0);
		Label no = new Label();
		int stack = 0;
		for (int i = 0; i < form.size(); i++) {
			Call call = form.get(i);
			for (int operand : call.operands()) method.visitVarInsn(Opcodes.ALOAD, operand);
			stack = Math.max(stack, call.operands().length);
			method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, call.owner(), call.name(), call.desc(), false);
			if (form.size() > 1) method.visitJumpInsn(Opcodes.IFEQ, no);
		}
		if (form.size() > 1) {
			method.visitInsn(Opcodes.ICONST_1);
			method.visitInsn(Opcodes.IRETURN);
			method.visitLabel(no);
			method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			method.visitInsn(Opcodes.ICONST_0);
		}
		method.visitInsn(Opcodes.IRETURN);
		method.visitMaxs(stack, Type.getArgumentsAndReturnSizes(seam.desc()) >> 2);
		return method;
	}
}
