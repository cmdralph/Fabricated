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
 * The frames Mixin 0.8 records for a target method, as its local variable computation reads them (CORPUS-79).
 *
 * <p>Mixin works out the locals live at an injection point ({@code Locals.getLocalsAt}, which local captures,
 * {@code @ModifyVariable} and MixinExtras' {@code @Local} read) by walking the method's instructions, with each frame's
 * change in size taken from the frames its class metadata recorded ({@code ClassInfo.FrameData}). It records those from
 * the class as read without expanding its frames, and records an appending or chopping frame's size as at least the
 * method's argument slots ({@code computeFrameSize(frame, initialFrameSize)}), not the number of locals it appends or
 * chops. A frame appending fewer locals than the method's argument slots then counts as appending that many, and one
 * chopping fewer counts as chopping that many: a chop after such an append leaves Mixin thinking fewer locals remain
 * than do, and it drops the live ones beyond. In the code Mixin walks, which ModLauncher reads with its frames expanded,
 * every frame states its locals.
 *
 * <p>Forge 61's and 64's {@code ScreenEffectRenderer.renderScreenEffect} has four argument slots, appends three locals
 * ({@code PoseStack}, {@code Player}, a pair), then chops one: Mixin counted 4 + 4 - 4 and dropped the {@code PoseStack}
 * Resourceful Lib's {@code @Local} takes, so the client crashed ("Found 0 candidate variables"). Fabric's Mixin computes the
 * locals the same way, but vanilla's method has other frames, and the mod works there.
 *
 * <p>The repair (Fabricated's Mixin extension on Forge) records such a chop as a full frame of the locals it states, which
 * Mixin reads as it reads any full frame: the locals the frame states, and none beyond. Every other frame stays as Mixin
 * recorded it, so the locals Mixin finds elsewhere in the method don't change.
 */
public final class RecordedFrames {
	private RecordedFrames() {
	}

	/**
	 * A frame as Mixin's class metadata records it.
	 *
	 * @param index the frame's instruction index in the method ({@code FrameData.index})
	 * @param type  its type as the class's bytes have it ({@code F_APPEND}, {@code F_CHOP}, {@code F_SAME}, ...)
	 * @param size  its size as Mixin records it ({@code FrameData.size})
	 */
	public record Frame(int index, int type, int size) {
	}

	/**
	 * The chopping frames, by their position in {@code recorded}, after which Mixin's local computation counts fewer locals
	 * than the frame states, and so drops live ones. Each is judged as Mixin counts once the earlier ones are repaired,
	 * recorded as full frames of the locals they state. Only a method whose frames are all expanded and line up with the
	 * recorded ones, one for one and at the same instructions, is judged; any other gives none.
	 *
	 * @param method   the method as Mixin walks it: read with its frames expanded, before any mixin applies
	 * @param recorded the frames Mixin recorded for it, in order
	 * @return the positions, ascending; empty where no chop drops a live local
	 */
	public static List<Integer> droppingChops(MethodNode method, List<Frame> recorded) {
		List<FrameNode> frames = frames(method);
		if (frames.isEmpty() || frames.size() != recorded.size()) return List.of();
		int initial = firstNonArgumentSlot(method);
		List<Integer> chops = new ArrayList<>();
		int size = initial;
		for (int i = 0; i < frames.size(); i++) {
			FrameNode frame = frames.get(i);
			Frame data = recorded.get(i);
			if (frame.type != Opcodes.F_NEW || data.index() != method.instructions.indexOf(frame)) return List.of();
			int stated = Math.max(initial, slots(frame.local));
			size = switch (data.type()) {
				case Opcodes.F_FULL -> Math.max(initial, Math.min(stated, data.size()));
				case Opcodes.F_NEW -> Math.max(initial, data.size());
				case Opcodes.F_APPEND -> size + data.size();
				case Opcodes.F_CHOP -> Math.max(initial, size - data.size());
				default -> size;
			};
			if (data.type() == Opcodes.F_CHOP && size < stated) {
				chops.add(i);
				size = stated; // as Mixin reads the full frame the repair records
			}
		}
		return chops;
	}

	/**
	 * The method's frames as full frames of the locals and stack each states, at the same instructions, every other
	 * instruction a {@code NOP}: what Mixin records the frames from ({@code ClassInfo.Method}'s constructor) to give each
	 * the size of the locals it states. Only its access, name, descriptor and instructions are set.
	 *
	 * @param method a method read with its frames expanded
	 */
	public static MethodNode asFullFrames(MethodNode method) {
		MethodNode full = new MethodNode(method.access, method.name, method.desc, null, null);
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof FrameNode frame && frame.type == Opcodes.F_NEW) {
				Object[] local = frame.local == null ? new Object[0] : frame.local.toArray();
				Object[] stack = frame.stack == null ? new Object[0] : frame.stack.toArray();
				full.instructions.add(new FrameNode(Opcodes.F_FULL, local.length, local, stack.length, stack));
			} else {
				full.instructions.add(new InsnNode(Opcodes.NOP));
			}
		}
		return full;
	}

	private static List<FrameNode> frames(MethodNode method) {
		List<FrameNode> frames = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) {
			if (insn instanceof FrameNode frame) frames.add(frame);
		}
		return frames;
	}

	/** The slots an expanded frame's locals take: a long or a double two, anything else one. */
	private static int slots(List<Object> locals) {
		if (locals == null) return 0;
		int slots = 0;
		for (Object local : locals) slots += local == Opcodes.LONG || local == Opcodes.DOUBLE ? 2 : 1;
		return slots;
	}

	/** The first slot after {@code this} and the arguments, as Mixin's {@code Bytecode.getFirstNonArgLocalIndex}. */
	private static int firstNonArgumentSlot(MethodNode method) {
		int slot = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
		for (Type argument : Type.getArgumentTypes(method.desc)) slot += argument.getSize();
		return slot;
	}
}
