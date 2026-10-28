package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import io.github.cmdralph.fabricated.core.bytecode.Annotations;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Verdict;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Range;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Injector repairs for lambdas the game builds differently: lambda bodies NeoForge's build numbers otherwise, calls
 * inside lambdas, and the values a lambda captures in another order or with one added.
 */
final class LambdaRepairs extends PreflightPart {
	LambdaRepairs(PreflightClasses classes) {
		super(classes);
	}

	/**
	 * Points the selectors of an injector that name a missing lambda ({@code lambda$enclosing$N}) at the only lambda of
	 * the same enclosing method and descriptor that every target has under one other name, and returns the selectors'
	 * original names. A selector without a descriptor is matched by the parameters an {@code @Inject} handler takes,
	 * which are the lambda's (Ledger's portal hook on 1.21.11, whose lambdas NeoForge's recompiled build numbers
	 * otherwise; LAMBDA-4); one taking none isn't judged.
	 */
	static List<String> lambdaRepairs(MethodNode handler, AnnotationNode injector, List<ClassNode> targets, Context ctx) {
		if (!(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		List<String> repaired = new ArrayList<>();
		next:
		for (int i = 0; i < selectors.size(); i++) {
			if (!(selectors.get(i) instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			Matcher lambda = ref.fuzzy() || ref.name() == null ? null : MixinNames.LAMBDA.matcher(ref.name());
			if (lambda == null || !lambda.matches()) continue;
			List<Type> params = ref.desc() == null ? lambdaParameters(handler, injector) : null;
			if (ref.desc() == null && params == null) continue;
			String prefix = "lambda$" + lambda.group(1) + "$";
			MethodNode renamed = null;
			for (ClassNode target : targets) {
				if (ref.desc() == null && target.methods.stream().anyMatch(m -> m.name.equals(ref.name()))) continue next;
				MethodNode only = null;
				for (MethodNode m : target.methods) {
					if (ref.desc() != null ? !m.desc.equals(ref.desc()) : !List.of(Type.getArgumentTypes(m.desc)).equals(params)
							|| Type.getReturnType(m.desc).equals(Type.VOID_TYPE) == returnsValue(handler)) continue;
					if (m.name.equals(ref.name())) continue next; // present: nothing to repair
					if (!m.name.startsWith(prefix) || !MixinNames.LAMBDA.matcher(m.name).matches()) continue;
					if (only != null) {
						only = null;
						break; // ambiguous
					}
					only = m;
				}
				if (only == null || (renamed != null && !(renamed.name + renamed.desc).equals(only.name + only.desc))) {
					renamed = null;
					break;
				}
				renamed = only;
			}
			if (renamed == null) continue;
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.set(i, renamed.name + renamed.desc);
			repaired.add(ref.name() + (ref.desc() != null ? ref.desc() : ""));
		}
		return repaired;
	}

	/**
	 * Points the selectors of an injector that name a lambda ({@code lambda$enclosing$N}) by vanilla's numbering at the
	 * lambda this game build creates in its place, when vanilla's code is at hand (VANILLA-1), and returns the selectors'
	 * original names. NeoForge's patches add lambdas, which numbers the class's later ones otherwise: its {@code Blocks}
	 * patch makes the powered rail's constructor reference a lambda, so Wilder Wild's hooks into the ice blocks' spawn
	 * predicates named lambdas of another shape on every version, and a hook into one of the same shape would have
	 * applied to the wrong lambda without a word (LAMBDA-5). Forge 47 names vanilla's lambdas by their SRG names, which
	 * its patched classes, compiled from Forge's sources, don't keep: theirs carry javac's names, so a hook naming
	 * vanilla's ({@code m_289088_}) found nothing, or another lambda, which Forge's build named as vanilla's where javac
	 * numbered it as Mojang's names number vanilla's (LAMBDA-6). The lambdas the creating method makes are aligned with
	 * vanilla's by their shape and code ({@link PatchAlignment#places}); a lambda that another lambda creates isn't
	 * followed, nor one whose place the alignment can't tell. A lambda of vanilla's under its SRG name, which no lambda
	 * of this build carries, is also found where Forge changed its code or what it captures: by its place where the
	 * creating method makes lambdas of the same interfaces in the same order in vanilla's class and in this build's, or
	 * as the one lambda of its interface and signature there; and in a lambda found so in its turn.
	 */
	List<String> vanillaLambdaRepairs(AnnotationNode injector, List<ClassNode> targets, Context ctx) {
		if (!(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		List<String> repaired = new ArrayList<>();
		next:
		for (int i = 0; i < selectors.size(); i++) {
			if (!(selectors.get(i) instanceof String raw)) continue;
			MemberRef ref = MemberRef.parse(ctx.resolve(raw));
			if (ref.fuzzy() || ref.name() == null || ref.name().startsWith("<")) continue;
			// A name of another form is followed where this build lacks it or gives it a lambda of its own: a lambda of
			// vanilla's under its SRG name, which Forge 47's build gives the lambda its javac numbering names as vanilla's.
			if (!MixinNames.LAMBDA.matcher(ref.name()).matches() && targets.stream().anyMatch(t -> namesOtherThanLambda(t, ref))) continue;
			MethodNode placed = null;
			for (ClassNode target : targets) {
				MethodNode here = lambdaInVanillasPlace(target, ref);
				if (here == null || placed != null && !(placed.name + placed.desc).equals(here.name + here.desc)) continue next;
				placed = here;
			}
			if (placed == null || placed.name.equals(ref.name()) && (ref.desc() == null || placed.desc.equals(ref.desc()))) continue;
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.set(i, placed.name + placed.desc);
			repaired.add(ref.name() + (ref.desc() != null ? ref.desc() : ""));
		}
		return repaired;
	}

	/**
	 * The lambda of {@code target} that this game build creates where vanilla's version of the class creates the one
	 * {@code ref} names, or null when that can't be told.
	 */
	private MethodNode lambdaInVanillasPlace(ClassNode target, MemberRef ref) {
		ClassNode original = vanillaClass(target.name);
		if (original == null) return null;
		MethodNode lambda = null;
		for (MethodNode m : original.methods) {
			if (!m.name.equals(ref.name()) || ref.desc() != null && !m.desc.equals(ref.desc())) continue;
			if (lambda != null) return null;
			lambda = m;
		}
		if (lambda == null || !isLambdaBody(lambda)) return null;
		return lambdaPlace(original, target, lambda, !MixinNames.LAMBDA.matcher(ref.name()).matches(), 0);
	}

	/**
	 * The lambda of {@code target} in the place of vanilla's {@code lambda} in {@code original}, or null when that can't
	 * be told. Only a lambda {@code renamed} (vanilla's under its SRG name, LAMBDA-6) is found by its interface alone or
	 * in a lambda that another lambda creates, a few levels deep.
	 */
	private MethodNode lambdaPlace(ClassNode original, ClassNode target, MethodNode lambda, boolean renamed, int depth) {
		MethodNode creator = null;
		for (MethodNode m : original.methods) {
			if (!createsLambda(m, lambda)) continue;
			if (creator != null) return null;
			creator = m;
		}
		if (creator == null) return null;
		MethodNode patched = null;
		if (!isLambdaBody(creator)) {
			for (MethodNode m : target.methods) {
				if (m.name.equals(creator.name) && m.desc.equals(creator.desc)) patched = m;
			}
		} else if (renamed && depth < 3) {
			patched = lambdaPlace(original, target, creator, true, depth + 1);
		}
		if (patched == null) return null;
		List<CreatedLambda> vanillas = createdLambdas(original, creator);
		List<CreatedLambda> games = createdLambdas(target, patched);
		int index = 0;
		while (index < vanillas.size() && vanillas.get(index).body() != lambda) index++;
		int[] places = PatchAlignment.places(vanillas.stream().map(CreatedLambda::shape).toArray(String[]::new),
				games.stream().map(CreatedLambda::shape).toArray(String[]::new));
		if (index == vanillas.size()) return null;
		if (places != null && places[index] >= 0) return games.get(places[index]).body();
		if (!renamed) return null;
		if (vanillas.stream().map(CreatedLambda::face).toList().equals(games.stream().map(CreatedLambda::face).toList())) return games.get(index).body();
		CreatedLambda only = onlyOfInterface(games, vanillas.get(index).face());
		return onlyOfInterface(vanillas, vanillas.get(index).face()) != null && only != null ? only.body() : null;
	}

	/** The one lambda of {@code lambdas} with {@code face}, or null when there are none or several. */
	private static CreatedLambda onlyOfInterface(List<CreatedLambda> lambdas, String face) {
		CreatedLambda only = null;
		for (CreatedLambda lambda : lambdas) {
			if (!lambda.face().equals(face)) continue;
			if (only != null) return null;
			only = lambda;
		}
		return only;
	}

	/**
	 * A lambda a method creates: its body, its face (whether it is static, the interface, its method and the signature
	 * it takes there, which values the lambda captures leave alone) and its shape as alignment compares it (the creating
	 * instruction's type, its descriptor and code).
	 */
	private record CreatedLambda(MethodNode body, String face, String shape) {
	}

	/** The lambdas of {@code owner} that {@code method} creates, in the order it creates them. */
	private static List<CreatedLambda> createdLambdas(ClassNode owner, MethodNode method) {
		List<CreatedLambda> created = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof InvokeDynamicInsnNode indy)) continue;
			for (Object arg : indy.bsmArgs) {
				if (!(arg instanceof Handle h) || !h.getOwner().equals(owner.name)) continue;
				for (MethodNode m : owner.methods) {
					if (m.name.equals(h.getName()) && m.desc.equals(h.getDesc()) && isLambdaBody(m)) {
						Object instantiated = indy.bsmArgs.length > 2 ? indy.bsmArgs[2] : m.desc;
						String kind = (m.access & Opcodes.ACC_STATIC) != 0 ? "static " : "";
						created.add(new CreatedLambda(m, kind + Type.getReturnType(indy.desc) + "." + indy.name + instantiated,
								indy.name + indy.desc + " " + m.desc + "\n" + PatchAlignment.code(m)));
					}
				}
			}
		}
		return created;
	}

	/**
	 * Whether a method an {@code invokedynamic} names is a lambda's body: named as javac names one, or synthetic, as a
	 * lambda renamed by obfuscation and SRG's names is ({@code m_289088_} on Forge 47; LAMBDA-6). A method reference
	 * names a method of the class's own, which isn't synthetic.
	 */
	private static boolean isLambdaBody(MethodNode method) {
		return MixinNames.LAMBDA.matcher(method.name).matches() || (method.access & Opcodes.ACC_SYNTHETIC) != 0;
	}

	/** Whether {@code owner} has a method {@code ref} names (by its descriptor too, where it has one) that isn't a lambda's body. */
	private static boolean namesOtherThanLambda(ClassNode owner, MemberRef ref) {
		for (MethodNode m : owner.methods) {
			if (m.name.equals(ref.name()) && (ref.desc() == null || m.desc.equals(ref.desc())) && !isLambdaBody(m)) return true;
		}
		return false;
	}

	/** Whether an {@code @Inject} handler takes a returnable callback info: its target returns a value. */
	private static boolean returnsValue(MethodNode handler) {
		List<Type> args = PreflightAsm.handlerArgs(handler);
		return args != null && !args.isEmpty() && args.get(args.size() - 1).getDescriptor().equals(MixinNames.CALLBACK_INFO_RETURNABLE);
	}

	/** The parameters an {@code @Inject} handler takes before its callback info (and any sugar), or null when it takes none. */
	private static List<Type> lambdaParameters(MethodNode handler, AnnotationNode injector) {
		List<Type> args = injector.desc.equals(MixinNames.INJECT) ? PreflightAsm.handlerArgs(handler) : null;
		if (args == null || args.size() < 2 || !PreflightAsm.isCallbackInfo(args.get(args.size() - 1))) return null;
		return args.subList(0, args.size() - 1);
	}

	/**
	 * Gives an {@code @Inject} into a lambda a bridge taking the lambda's parameters as this game build has them, when
	 * NeoForge's build captures the same values in another order, or more of them: javac orders a lambda's captured values
	 * by their first use in it, which NeoForge's patches change (Wover's hook into {@code WorldLoader}'s registry load,
	 * FrozenLib's into {@code RegistryDataLoader}'s; LAMBDA-3). The bridge passes each of the handler's parameters the
	 * lambda's of its type: in order, when the handler's types occur in the lambda's that way only once; otherwise by
	 * type, where a type the lambda has more than once is only passed to a parameter the handler never uses; otherwise by
	 * how vanilla's lambda and this build's use them ({@link #byUse}). A method NeoForge gave more parameters gets the
	 * same bridge, in order only (Ledger's hooks into {@code FireBlock.checkBurnOut}, which takes a face too; SIG-1). Where
	 * the running Mixin refuses a static handler on a method that takes {@code this}, and this build's lambda takes it
	 * where vanilla's doesn't, the bridge takes {@code this} (Forge 47's {@code MouseHandler}, whose patch reads the
	 * client in its screen lambdas; LAMBDA-7). Returns a description of the bridged method.
	 */
	List<String> capturedValueRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		if (!injector.desc.equals(MixinNames.INJECT) || Annotations.value(injector, "locals") != null || (mixin.access & Opcodes.ACC_INTERFACE) != 0
				|| !(Annotations.value(injector, "method") instanceof List<?> selectors) || selectors.size() != 1
				|| annotatedParameters(handler.visibleParameterAnnotations) || annotatedParameters(handler.invisibleParameterAnnotations)) return List.of();
		List<MethodNode> methods = PreflightAsm.selectedMethods(injector, target, ctx);
		if (methods.size() != 1 || methods.get(0).name.startsWith("<")) return List.of();
		MethodNode lambda = methods.get(0);
		boolean isLambda = MixinNames.LAMBDA.matcher(lambda.name).matches();
		boolean handlerStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
		boolean lambdaStatic = (lambda.access & Opcodes.ACC_STATIC) != 0;
		boolean takesThis = handlerStatic && !lambdaStatic && exactStaticInjects;
		Type[] own = Type.getArgumentTypes(handler.desc);
		if (own.length < 2 || !PreflightAsm.isCallbackInfo(own[own.length - 1]) || handlerStatic != lambdaStatic && !takesThis) return List.of();
		List<Type> wanted = List.of(own).subList(0, own.length - 1);
		List<Type> captured = List.of(Type.getArgumentTypes(lambda.desc));
		// A patch captures a value or two more; a lambda taking many more is more likely another one under that name.
		if (wanted.equals(captured) && !takesThis || captured.size() > wanted.size() + 2) return List.of();
		int[] from = inOrder(wanted, captured);
		if (from == null && isLambda) from = byType(handler, wanted, captured);
		if (from == null && isLambda) from = byUse(vanillaLambdaTaking(target.name, wanted, lambda), lambda, wanted, captured);
		if (from == null) return List.of();

		List<Type> params = new ArrayList<>(captured);
		params.add(own[own.length - 1]);
		MethodNode bridge = HandlerBridges.bridgeMethod(lambdaStatic, HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated"), params,
				Type.getReturnType(handler.desc));
		int stack = 0;
		if (!handlerStatic) {
			bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
			stack++;
		}
		for (int index : from) {
			bridge.instructions.add(new VarInsnNode(params.get(index).getOpcode(Opcodes.ILOAD), HandlerBridges.slotOf(lambdaStatic, params, index)));
			stack += params.get(index).getSize();
		}
		bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, HandlerBridges.slotOf(lambdaStatic, params, params.size() - 1)));
		HandlerBridges.callHandler(bridge, mixin.name, handler, stack + 1);
		@SuppressWarnings("unchecked")
		List<Object> values = (List<Object>) selectors;
		values.set(0, lambda.name + lambda.desc);
		HandlerBridges.moveInjector(handler, injector, bridge);
		bridges.add(bridge);
		if (wanted.equals(captured)) {
			return List.of(lambda.name + lambda.desc + ", which this game build declares taking this, where " + loader
					+ "'s Mixin refuses a static handler; a bridge taking this calls the handler");
		}
		return List.of(lambda.name + lambda.desc + (isLambda ? ", whose captured values this game build passes in another order or with more"
				: ", which this game build declares with more parameters") + (takesThis ? ", taking this" : "")
				+ "; a bridge passes the handler the ones it takes");
	}

	/**
	 * Vanilla's lambda in {@code owner} taking {@code params} and returning what {@code lambda} returns, when it has one
	 * only; null otherwise, or without vanilla's class.
	 */
	private MethodNode vanillaLambdaTaking(String owner, List<Type> params, MethodNode lambda) {
		ClassNode original = vanillaClass(owner);
		if (original == null) return null;
		String desc = Type.getMethodDescriptor(Type.getReturnType(lambda.desc), params.toArray(Type[]::new));
		MethodNode only = null;
		for (MethodNode m : original.methods) {
			if (!m.desc.equals(desc) || !isLambdaBody(m)) continue;
			if (only != null) return null;
			only = m;
		}
		return only;
	}

	/**
	 * Where each of {@code wanted}, the parameters of {@code vanilla}'s lambda, is in {@code captured}, this build's
	 * {@code lambda}'s, by how the two bodies use them: a type each has once is placed by type; one they have more than once,
	 * where the same use of it is in both (passed as the same argument of the same call, or compared with the same
	 * constant) and in no other parameter of that type of this build's (Forge 47's {@code KeyboardHandler.keyPress}
	 * lambda, whose patch reordered the screen and the result array around four {@code int}s; LAMBDA-7). Null unless every
	 * parameter is placed, one to one.
	 */
	private static int[] byUse(MethodNode vanilla, MethodNode lambda, List<Type> wanted, List<Type> captured) {
		if (vanilla == null || wanted.size() != captured.size()) return null;
		List<Set<String>> before = parameterUses(vanilla);
		List<Set<String>> after = parameterUses(lambda);
		int[] from = new int[wanted.size()];
		boolean[] taken = new boolean[captured.size()];
		for (int i = 0; i < wanted.size(); i++) {
			Type type = wanted.get(i);
			boolean once = Collections.frequency(wanted, type) == 1 && Collections.frequency(captured, type) == 1;
			int found = -1;
			for (int j = 0; j < captured.size(); j++) {
				if (!captured.get(j).equals(type) || !once && Collections.disjoint(before.get(i), after.get(j))) continue;
				if (found >= 0) return null;
				found = j;
			}
			if (found < 0 || taken[found]) return null;
			taken[found] = true;
			from[i] = found;
		}
		return from;
	}

	/**
	 * For each parameter of {@code method}, the uses of its value seen where it is loaded right before what takes it: an
	 * argument of a call (its opcode, {@code owner.name desc} and the argument's place) or an operand of a comparison, with
	 * the other operand where that is a constant. A parameter the method stores to has none.
	 */
	private static List<Set<String>> parameterUses(MethodNode method) {
		Type[] args = Type.getArgumentTypes(method.desc);
		Map<Integer, Integer> bySlot = new HashMap<>();
		int slot = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		for (int i = 0; i < args.length; i++) {
			bySlot.put(slot, i);
			slot += args[i].getSize();
		}
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof VarInsnNode v && v.getOpcode() >= Opcodes.ISTORE && v.getOpcode() <= Opcodes.ASTORE) bySlot.remove(v.var);
			if (insn instanceof IincInsnNode inc) bySlot.remove(inc.var);
		}
		List<Set<String>> uses = new ArrayList<>();
		for (int i = 0; i < args.length; i++) uses.add(new HashSet<>());
		for (AbstractInsnNode insn : method.instructions) {
			String use;
			int operands;
			if (insn instanceof MethodInsnNode call) {
				use = call.getOpcode() + " " + call.owner + "." + call.name + call.desc;
				operands = Type.getArgumentTypes(call.desc).length + (call.getOpcode() == Opcodes.INVOKESTATIC ? 0 : 1);
			} else if (insn instanceof JumpInsnNode jump && jump.getOpcode() >= Opcodes.IFEQ && jump.getOpcode() <= Opcodes.IF_ACMPNE) {
				use = Integer.toString(jump.getOpcode());
				operands = jump.getOpcode() >= Opcodes.IF_ICMPEQ ? 2 : 1;
			} else {
				continue;
			}
			AbstractInsnNode[] pushed = pushedBefore(insn, operands);
			for (int n = 0; n < operands; n++) {
				if (!(pushed[n] instanceof VarInsnNode load) || load.getOpcode() > Opcodes.ALOAD || !bySlot.containsKey(load.var)) continue;
				String other = operands == 2 && !(insn instanceof MethodInsnNode) ? " " + constant(pushed[1 - n]) : "";
				uses.get(bySlot.get(load.var)).add(use + " #" + n + other);
			}
		}
		return uses;
	}

	/**
	 * The instructions pushing the last of {@code operands} values {@code insn} takes, each a load or a constant right before
	 * it (null where another instruction pushes it, and for those before).
	 */
	private static AbstractInsnNode[] pushedBefore(AbstractInsnNode insn, int operands) {
		AbstractInsnNode[] pushed = new AbstractInsnNode[operands];
		AbstractInsnNode at = insn.getPrevious();
		for (int n = operands - 1; n >= 0; n--) {
			while (at != null && at.getOpcode() < 0) at = at.getPrevious();
			if (at == null || !(at instanceof VarInsnNode v && v.getOpcode() <= Opcodes.ALOAD) && constant(at) == null) break;
			pushed[n] = at;
			at = at.getPrevious();
		}
		return pushed;
	}

	/** The value an instruction pushing a constant pushes, as text; null for another instruction. */
	private static String constant(AbstractInsnNode insn) {
		if (insn == null) return null;
		int op = insn.getOpcode();
		if (op == Opcodes.ACONST_NULL) return "null";
		if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) return Integer.toString(op - Opcodes.ICONST_0);
		if (op >= Opcodes.LCONST_0 && op <= Opcodes.DCONST_1) return "const" + op;
		if (insn instanceof IntInsnNode push && (op == Opcodes.BIPUSH || op == Opcodes.SIPUSH)) return Integer.toString(push.operand);
		if (insn instanceof LdcInsnNode ldc) return String.valueOf(ldc.cst);
		return null;
	}

	/** Where each of {@code wanted} is in {@code captured}, taken in order, when they occur in it that way only once; else null. */
	private static int[] inOrder(List<Type> wanted, List<Type> captured) {
		int n = wanted.size();
		int m = captured.size();
		// ways[i][j]: in how many ways (up to 2) wanted from i on occurs in order in captured from j on.
		int[][] ways = new int[n + 1][m + 1];
		for (int j = 0; j <= m; j++) ways[n][j] = 1;
		for (int i = n - 1; i >= 0; i--) {
			for (int j = m - 1; j >= 0; j--) {
				ways[i][j] = Math.min(2, ways[i][j + 1] + (wanted.get(i).equals(captured.get(j)) ? ways[i + 1][j + 1] : 0));
			}
		}
		if (ways[0][0] != 1) return null;
		int[] from = new int[n];
		for (int i = 0, j = 0; i < n; j++) {
			if (wanted.get(i).equals(captured.get(j)) && ways[i + 1][j + 1] == 1) from[i++] = j;
		}
		return from;
	}

	/**
	 * Where each of {@code wanted} is in {@code captured} by its type: the k-th parameter of a type goes to the lambda's
	 * k-th of that type, which is only certain where the lambda has one; a type it has more than once only for a
	 * parameter the handler never uses. Null when a type is missing, or such a parameter is used.
	 */
	private static int[] byType(MethodNode handler, List<Type> wanted, List<Type> captured) {
		int[] from = new int[wanted.size()];
		Map<Type, Integer> seen = new HashMap<>();
		int slot = (handler.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
		for (int i = 0; i < wanted.size(); i++) {
			Type type = wanted.get(i);
			int k = seen.merge(type, 1, Integer::sum) - 1;
			List<Integer> at = new ArrayList<>();
			for (int j = 0; j < captured.size(); j++) {
				if (captured.get(j).equals(type)) at.add(j);
			}
			if (at.size() <= k || at.size() > 1 && usesSlot(handler, slot)) return null;
			from[i] = at.get(k);
			slot += type.getSize();
		}
		return from;
	}

	/** Whether {@code method} reads or writes the local variable in {@code slot}. */
	private static boolean usesSlot(MethodNode method, int slot) {
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof VarInsnNode v && v.var == slot || insn instanceof IincInsnNode inc && inc.var == slot) return true;
		}
		return false;
	}

	/** The injectors at a call or field access whose handler takes only what that instruction takes and gives. */
	private static final Set<String> CALL_SITE_INJECTORS = Set.of(MixinNames.WRAP_OPERATION, MixinNames.MODIFY_EXPRESSION_VALUE, MixinNames.REDIRECT,
			"Lorg/spongepowered/asm/mixin/injection/ModifyArg;", "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
			"Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;", "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;");

	/**
	 * Adds to a call-site injector's methods the lambda a target method's call moved into: when the call or field access
	 * it hooks is in none of that method's instructions but in exactly one lambda the method creates, as where NeoForge's
	 * build runs vanilla's code from an {@code Optional} callback (FrozenLib's registry call in
	 * {@code RegistryDataLoader.loadElementFromResource}, on 1.21.11; LAMBDA-2). The handler takes the instruction's own
	 * receiver, arguments or result, which are the same in the lambda; one that takes MixinExtras sugar (the method's
	 * locals), isn't static where the lambda is, or whose point has an ordinal or a slice, isn't moved. Returns a
	 * description of each lambda added.
	 */
	static List<String> lambdaCallRepairs(AnnotationNode injector, MethodNode handler, ClassNode target, Context ctx) {
		List<AnnotationNode> ats = Annotations.list(Annotations.value(injector, "at"));
		if (!CALL_SITE_INJECTORS.contains(injector.desc) || Annotations.value(injector, "slice") != null || ats.size() != 1
				|| annotatedParameters(handler.visibleParameterAnnotations) || annotatedParameters(handler.invisibleParameterAnnotations)
				|| !(Annotations.value(injector, "method") instanceof List<?> selectors)) return List.of();
		AnnotationNode at = ats.get(0);
		String point = Annotations.value(at, "value") instanceof String v ? v.toUpperCase(Locale.ROOT) : "";
		if (!Set.of("INVOKE", "INVOKE_ASSIGN", "FIELD", "NEW").contains(point) || Annotations.value(at, "ordinal") != null
				|| !(Annotations.value(at, "target") instanceof String raw)) return List.of();
		String resolved = ctx.resolve(raw);
		int opcode = Annotations.value(at, "opcode") instanceof Integer i ? i : -1;
		List<String> added = new ArrayList<>();
		for (MethodNode method : PreflightAsm.selectedMethods(injector, target, ctx)) {
			if (InjectionPoints.count(point, resolved, -1, opcode, Range.ALL, method) != 0) continue;
			MethodNode only = null;
			for (MethodNode lambda : target.methods) {
				if (!lambda.name.startsWith("lambda$" + method.name + "$") || !createsLambda(method, lambda)
						|| InjectionPoints.count(point, resolved, -1, opcode, Range.ALL, lambda) <= 0) continue;
				if (only != null) {
					only = null;
					break; // in two lambdas: not judged
				}
				only = lambda;
			}
			if (only == null || (only.access & Opcodes.ACC_STATIC) != 0 && (handler.access & Opcodes.ACC_STATIC) == 0) continue;
			@SuppressWarnings("unchecked")
			List<Object> values = (List<Object>) selectors;
			values.add(only.name + only.desc);
			added.add(MemberRef.parse(resolved).name() + " in " + method.name + ", which this game build makes in its lambda " + only.name
					+ "; the lambda is added to its methods");
		}
		return added;
	}

	/** Whether {@code method} creates {@code lambda} (its {@code invokedynamic} names the lambda's body). */
	private static boolean createsLambda(MethodNode method, MethodNode lambda) {
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof InvokeDynamicInsnNode indy)) continue;
			for (Object arg : indy.bsmArgs) {
				if (arg instanceof Handle h && h.getName().equals(lambda.name) && h.getDesc().equals(lambda.desc)) return true;
			}
		}
		return false;
	}

	private static boolean annotatedParameters(List<AnnotationNode>[] byParameter) {
		if (byParameter == null) return false;
		for (List<AnnotationNode> annotations : byParameter) {
			if (annotations != null && !annotations.isEmpty()) return true;
		}
		return false;
	}

	/**
	 * Bridges an {@code @Inject} handler taking the parameters vanilla's method has, then the callback info and MixinExtras
	 * sugar, into the one method its selector picks, which NeoForge's build declares with one more parameter: the bridge
	 * takes the method's parameters, and the sugar, and calls the handler without the added one, which Mixin would refuse.
	 * {@link #capturedValueRepairs} bridges such a handler without sugar (SIG-1); Ledger's hooks into
	 * {@code FireBlock.checkBurnOut}, which NeoForge's build gives the side the fire burns from, take a {@code @Local}
	 * after the callback info. The injection points must be in that method. Returns a description of the bridge.
	 */
	static List<String> addedParameterRepairs(ClassNode mixin, MethodNode handler, AnnotationNode injector, ClassNode target, Context ctx,
			List<MethodNode> bridges) {
		if (!injector.desc.equals(MixinNames.INJECT) || Annotations.value(injector, "locals") != null || (mixin.access & Opcodes.ACC_INTERFACE) != 0
				|| !(Annotations.value(injector, "method") instanceof List<?> selectors) || selectors.size() != 1
				|| !(selectors.get(0) instanceof String raw)) return List.of();
		List<Type> args = PreflightAsm.handlerArgs(handler);
		if (args == null || args.size() < 2 || !PreflightAsm.isCallbackInfo(args.get(args.size() - 1))) return List.of();
		MemberRef ref = MemberRef.parse(ctx.resolve(raw));
		if (ref.fuzzy() || ref.name() == null) return List.of();
		MethodNode chosen = target.methods.stream().filter(m -> ref.matchesMethod(m.name, m.desc)).findFirst().orElse(null);
		if (chosen == null) return List.of();
		List<Type> own = args.subList(0, args.size() - 1);
		// The sugar follows the callback info, where the bridge takes it over.
		if (!List.of(Type.getArgumentTypes(handler.desc)).subList(0, args.size()).equals(args)) return List.of();
		String vanilla = Type.getMethodDescriptor(Type.getReturnType(chosen.desc), own.toArray(Type[]::new));
		int inserted = CallRepairs.insertedArgument(vanilla, chosen.desc);
		if (inserted < 0 || InjectionPoints.pointsIn(Annotations.list(Annotations.value(injector, "at")),
				Annotations.list(Annotations.value(injector, "slice")), chosen, ctx) != Verdict.FOUND) return List.of();
		bridges.add(HandlerBridges.insertedParameterBridge(mixin.name, handler, injector, 0, own.size(), chosen, inserted,
				HandlerBridges.freeName(mixin, bridges, handler.name + "$fabricated")));
		return List.of(chosen.name + vanilla + ", which this game build declares with one more parameter, " + chosen.name + chosen.desc
				+ "; the handler is called through a bridge taking it");
	}
}
