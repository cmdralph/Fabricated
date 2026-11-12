package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Range;
import io.github.cmdralph.fabricated.core.mixin.InjectionPoints.Verdict;
import io.github.cmdralph.fabricated.core.mixin.MixinPreflight.Context;

/** Where pre-flight finds an {@code @At}'s instructions in a method, as Mixin's injection points find them. */
class InjectionPointsTest {
	private static final String ENTITY = "net/minecraft/world/entity/LivingEntity";
	private static final String ON_GROUND = "L" + ENTITY + ";onGround()Z";
	private static final String FALL_DISTANCE = "L" + ENTITY + ";fallDistance:F";

	/**
	 * As vanilla's {@code LivingEntity.travel}, cut down: three calls of {@code onGround()} (instructions 1, 4 and 7), then
	 * a read of a field into a local (10, 11) and its write back from it (13, 14).
	 */
	private static MethodNode travel() {
		MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "travel", "()V", null, null);
		for (int i = 0; i < 3; i++) {
			method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
			method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, ENTITY, "onGround", "()Z"));
			method.instructions.add(new InsnNode(Opcodes.POP));
		}
		method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY, "fallDistance", "F"));
		method.instructions.add(new VarInsnNode(Opcodes.FSTORE, 1));
		method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		method.instructions.add(new VarInsnNode(Opcodes.FLOAD, 1));
		method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, ENTITY, "fallDistance", "F"));
		method.instructions.add(new InsnNode(Opcodes.RETURN));
		return method;
	}

	private static AnnotationNode at(Object... values) {
		AnnotationNode at = new AnnotationNode(MixinNames.AT);
		at.values = new ArrayList<>(List.of(values));
		return at;
	}

	private static Context context() throws Exception {
		return new Context("test.mixins.json", "com.example.mixin.TravelMixin", MixinConfigFile.parse("{\"package\":\"com.example.mixin\"}"), Refmap.EMPTY,
				false, true, false, Map.of(), Set.of(), new ArrayList<>(), null, "NeoForge", new HashSet<>());
	}

	/**
	 * PRE-30: Mixin's {@code BeforeInvoke} (and {@code AfterInvoke}, made from it) reads no {@code opcode}, so a call point
	 * with one finds the calls it finds without: Apoli's slipperiness hook names {@code onGround()} with
	 * {@code opcode = 180} (a field read's), Wilder Wild's sculk vein hook {@code getOpposite()} with {@code opcode = 0}.
	 * Pre-flight took the opcode for the call's, found none and left the hooks out.
	 */
	@Test
	void aCallPointIgnoresItsOpcodeAsMixinDoes() throws Exception {
		MethodNode travel = travel();
		for (int opcode : new int[] {Opcodes.GETFIELD, 0}) {
			for (String point : List.of("INVOKE", "INVOKE_ASSIGN")) {
				String what = point + " with opcode " + opcode;
				assertEquals(3, InjectionPoints.count(point, ON_GROUND, -1, opcode, Range.ALL, travel), what);
				assertEquals(1, InjectionPoints.count(point, ON_GROUND, 2, opcode, Range.ALL, travel), what);
				assertEquals(List.of(7), InjectionPoints.indexes(point, ON_GROUND, 2, opcode, Range.ALL, travel), what);
				assertEquals(List.of(1, 4, 7), PreflightAsm.matching(travel, point, MemberRef.parse(ON_GROUND), opcode), what);
				assertEquals(Verdict.FOUND, InjectionPoints.pointsIn(List.of(at("value", point, "target", ON_GROUND, "opcode", opcode, "ordinal", 2)), List.of(),
						travel, context()), what);
			}
		}
	}

	/** A slice bounded by a call with an opcode starts at that call, as Mixin's slice finds it with the same point. */
	@Test
	void aSliceBoundByACallIgnoresItsOpcode() throws Exception {
		AnnotationNode slice = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Slice;");
		slice.values = new ArrayList<>(List.of("from", at("value", "INVOKE", "target", ON_GROUND, "opcode", Opcodes.GETFIELD, "ordinal", 1)));
		assertEquals(new Range(4, 15), InjectionPoints.sliceRange(slice, travel(), context()));
	}

	/**
	 * Mixin's {@code BeforeFieldAccess} keeps only the accesses of the {@code opcode} given where it is one of the four
	 * field opcodes, and takes any other as none.
	 */
	@Test
	void aFieldPointKeepsTheAccessesOfAFieldOpcodeOnly() {
		MethodNode travel = travel();
		assertEquals(List.of(10), InjectionPoints.indexes("FIELD", FALL_DISTANCE, -1, Opcodes.GETFIELD, Range.ALL, travel));
		assertEquals(List.of(14), InjectionPoints.indexes("FIELD", FALL_DISTANCE, -1, Opcodes.PUTFIELD, Range.ALL, travel));
		assertEquals(0, InjectionPoints.count("FIELD", FALL_DISTANCE, -1, Opcodes.GETSTATIC, Range.ALL, travel));
		for (int none : new int[] {-1, 0, Opcodes.INVOKEVIRTUAL}) {
			assertEquals(2, InjectionPoints.count("FIELD", FALL_DISTANCE, -1, none, Range.ALL, travel), "opcode " + none);
			assertEquals(List.of(10, 14), PreflightAsm.matching(travel, "FIELD", MemberRef.parse(FALL_DISTANCE), none), "opcode " + none);
		}
	}

	/** A local's point with {@code opcode = 0} looks for its type's loads, as Mixin's {@code InjectionPointData.getOpcode} gives them for 0. */
	@Test
	void aLocalPointsOpcodeOfZeroIsItsTypes() {
		MethodNode travel = travel();
		assertTrue(InjectionPoints.accessesAnyLocal(true, Type.FLOAT_TYPE, 0, -1, Range.ALL, travel));
		assertTrue(InjectionPoints.accessesAnyLocal(false, Type.FLOAT_TYPE, 0, -1, Range.ALL, travel));
		assertFalse(InjectionPoints.accessesAnyLocal(true, Type.FLOAT_TYPE, Opcodes.ILOAD, -1, Range.ALL, travel), "an opcode above 0 is honoured");
	}
}
