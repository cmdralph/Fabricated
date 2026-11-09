package io.github.cmdralph.fabricated.core.mixin;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The frames Mixin 0.8.7 records for a target method, and the locals its local computation counts from them (CORPUS-79).
 *
 * <p>Mixin works out the locals live at an injection point ({@code Locals.getLocalsAt}, which local captures,
 * {@code @ModifyVariable} and MixinExtras' {@code @Local} read) by walking the method's instructions, its count of the
 * locals at each frame taken from the frames its class metadata recorded ({@code ClassInfo.FrameData}), paired with the
 * frames it walks by their order. Mixin 0.8.7 reads that metadata with the class's frames compressed, unless its
 * {@code CLASSREADER_EXPAND_FRAMES} option is set, and records an appending or chopping frame's size as at least the
 * method's argument slots ({@code computeFrameSize(frame, initialFrameSize)}), not the slots it appends or chops. A frame
 * appending fewer locals than the method's argument slots then counts as appending that many, and one chopping fewer counts
 * as chopping that many. Forge's own ModLauncher hands Mixin the classes it walks with their frames compressed too, so
 * nothing in the walk corrects the count: where it is too low, Mixin drops the live locals beyond it.
 *
 * <p>Forge 61's and 64's {@code ScreenEffectRenderer.renderScreenEffect} has four argument slots, appends three locals
 * ({@code PoseStack}, {@code Player}, a pair), then chops one: Mixin counted 4 + 4 - 4 and dropped the {@code PoseStack}
 * Resourceful Lib's {@code @Local} takes, so the client crashed ("Found 0 candidate variables"). Fabric's Mixin, which
 * NeoForge runs, records and counts the frames the same way from its behaviour 0.10.0 (its {@code FrameData.rawSize},
 * without the floor, is read only at 0.9.2: FRAME-1); the mod works on Fabric, where vanilla's method (26.1.2's: six
 * argument slots, the {@code PoseStack} and {@code Player} appended in one frame, no chop) is counted too high but drops no
 * local.
 *
 * <p>The repair (Fabricated's Mixin extension on Forge) records the frames of a method Mixin would count wrong
 * ({@link #counted}) again, from the method with its frames expanded ({@link #withExpandedFrames}): Mixin records an
 * expanded frame's size as the locals it states, as it does with its {@code CLASSREADER_EXPAND_FRAMES} option, and counts
 * them right. A method Mixin counts right keeps the frames Mixin recorded.
 */
public final class RecordedFrames {
	private RecordedFrames() {
	}

	/**
	 * A frame as Mixin's class metadata records it.
	 *
	 * @param type its type as the bytes Mixin read have it ({@code F_APPEND}, {@code F_CHOP}, {@code F_NEW}, ...;
	 *             {@code FrameData.type})
	 * @param size its size as Mixin records it ({@code FrameData.size})
	 */
	public record Frame(int type, int size) {
	}

	/**
	 * The number of locals Mixin's local computation counts at each frame of a method it walks, from the frames it recorded
	 * for the method: {@code Locals.getLocalsAt}'s frame size, paired with the walked frames by their order, a walked frame
	 * stating the same locals as the one before skipped, a frame with nothing recorded counted from itself.
	 *
	 * @param method   the method as Mixin walks it, its frames compressed or expanded
	 * @param recorded the frames Mixin recorded for it, in order
	 * @return the count at each of the method's frames, in order
	 */
	public static int[] counted(MethodNode method, List<Frame> recorded) {
		int initial = firstNonArgumentSlot(method);
		List<FrameNode> walked = frames(method);
		int[] counted = new int[walked.size()];
		int size = initial;
		for (int i = 0; i < walked.size(); i++) {
			FrameNode frame = walked.get(i);
			if (frame.type != Opcodes.F_SAME && frame.type != Opcodes.F_SAME1) {
				int stated = frameSize(frame, initial);
				Frame data = i < recorded.size() ? recorded.get(i) : new Frame(frame.type, stated);
				size = switch (data.type()) {
					case Opcodes.F_FULL -> Math.max(initial, Math.min(stated, data.size()));
					case Opcodes.F_NEW -> Math.max(initial, data.size());
					case Opcodes.F_APPEND -> size + data.size();
					case Opcodes.F_CHOP -> Math.max(initial, size - data.size());
					default -> size;
				};
			}
			counted[i] = size;
		}
		return counted;
	}

	/**
	 * The method's frames expanded, as ASM reads a class with {@code EXPAND_FRAMES}: each one a new frame
	 * ({@code F_NEW}) of the locals and stack it states, at the same instruction, every other instruction a {@code NOP}.
	 * This is what Mixin records frames from ({@code ClassInfo.Method}'s constructor), to record each one's size as the
	 * locals it states. Only its access, name, descriptor and instructions are set; the frames' types are the method's own
	 * (an uninitialized value keeps its label).
	 *
	 * @param owner  the internal name of the method's class, the type of {@code this} in its first frame
	 * @param method the method, its frames compressed or expanded
	 */
	public static MethodNode withExpandedFrames(String owner, MethodNode method) {
		MethodNode expanded = new MethodNode(method.access, method.name, method.desc, null, null);
		List<Object> locals = implicitLocals(owner, method);
		for (AbstractInsnNode insn : method.instructions) {
			if (!(insn instanceof FrameNode frame)) {
				expanded.instructions.add(new InsnNode(Opcodes.NOP));
				continue;
			}
			List<Object> stack = List.of();
			switch (frame.type) {
				case Opcodes.F_NEW, Opcodes.F_FULL -> {
					locals = entries(frame.local);
					stack = entries(frame.stack);
				}
				case Opcodes.F_APPEND -> locals.addAll(entries(frame.local));
				case Opcodes.F_CHOP -> locals = new ArrayList<>(locals.subList(0, Math.max(0, locals.size() - entries(frame.local).size())));
				case Opcodes.F_SAME1 -> stack = entries(frame.stack);
				default -> {
					// F_SAME: the locals of the frame before, and no stack.
				}
			}
			expanded.instructions.add(new FrameNode(Opcodes.F_NEW, locals.size(), locals.toArray(), stack.size(), stack.toArray()));
		}
		return expanded;
	}

	/** The locals of a method's implicit first frame, as ASM computes them ({@code ClassReader.computeImplicitFrame}). */
	private static List<Object> implicitLocals(String owner, MethodNode method) {
		List<Object> locals = new ArrayList<>();
		if ((method.access & Opcodes.ACC_STATIC) == 0) locals.add("<init>".equals(method.name) ? Opcodes.UNINITIALIZED_THIS : owner);
		for (Type argument : Type.getArgumentTypes(method.desc)) {
			locals.add(switch (argument.getSort()) {
				case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> Opcodes.INTEGER;
				case Type.FLOAT -> Opcodes.FLOAT;
				case Type.LONG -> Opcodes.LONG;
				case Type.DOUBLE -> Opcodes.DOUBLE;
				case Type.ARRAY -> argument.getDescriptor();
				default -> argument.getInternalName();
			});
		}
		return locals;
	}

	private static List<Object> entries(List<Object> entries) {
		return entries == null ? new ArrayList<>() : new ArrayList<>(entries);
	}

	private static List<FrameNode> frames(MethodNode method) {
		List<FrameNode> frames = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof FrameNode frame) frames.add(frame);
		}
		return frames;
	}

	/**
	 * A frame's size as Mixin computes it ({@code Locals.computeFrameSize}): its locals' slots (a long or a double two,
	 * anything else one), and at least the method's argument slots.
	 */
	private static int frameSize(FrameNode frame, int initial) {
		if (frame.local == null) return initial;
		int slots = 0;
		for (Object local : frame.local) slots += local == Opcodes.LONG || local == Opcodes.DOUBLE ? 2 : 1;
		return Math.max(initial, slots);
	}

	/** The first slot after {@code this} and the arguments, as Mixin's {@code Bytecode.getFirstNonArgLocalIndex}. */
	private static int firstNonArgumentSlot(MethodNode method) {
		int slot = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
		for (Type argument : Type.getArgumentTypes(method.desc)) slot += argument.getSize();
		return slot;
	}
}
