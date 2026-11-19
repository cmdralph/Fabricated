package io.github.cmdralph.fabricated.core.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * LEAVE-6's guard in CI ({@link InvokerBridgeGuard}): on the burner of {@link AddedParametersTest}, whose platform's
 * method passes the side to the state's extension, whose default passes it to the block's, an override in the game's own
 * classes is judged as the code it overrides, from the same depth: OK where it ignores the side (the block's, a
 * state's passing it on only to the platform's hook, which ignores it); FAIL, naming it, where it reads it.
 */
class InvokerBridgeGuardTest {
	private static final String TNT = "net/minecraft/world/Tnt";
	private static final String SUB_STATE = "net/minecraft/world/SubState";

	@Test
	void anOverrideIgnoringTheParameterIsOk() {
		InvokerBridgeGuard.Result result = guard(tnt(false));
		assertTrue(result.ok(), result.toString());
		assertEquals(List.of("Burner.burn with Side.WEST"), result.bridged());
		assertEquals(List.of("SubState.flammability", "Tnt.flammability"), result.overrides());
		assertEquals("FABRICATED-GUARD invoker bridge overrides OK in 5 ms: Burner.burn with Side.WEST; overrides ignoring the parameter:"
				+ " SubState.flammability, Tnt.flammability", result.line(5));
	}

	@Test
	void anOverrideReadingTheParameterFailsNamingIt() {
		InvokerBridgeGuard.Result result = guard(tnt(true));
		assertFalse(result.ok());
		assertEquals(List.of("Burner.burn with Side.WEST"), result.bridged());
		assertEquals(List.of("Burner.burn: Tnt.flammability, overriding IBlockExtension.flammability, doesn't ignore it: Tnt.flammability calls"
				+ " Side.ordinal on the Side"), result.reading());
		assertTrue(result.line(5).startsWith("FABRICATED-GUARD invoker bridge overrides FAIL in 5 ms: Burner.burn with Side.WEST; Burner.burn: Tnt.flammability"),
				result.line(5));
	}

	/** A game whose classes but the burner are none of vanilla's, with the block {@code tnt}: no bridge, nothing to guard. */
	@Test
	void aClassThePlatformLeftAsVanillasIsntJudged() {
		Map<String, byte[]> game = AddedParametersTest.baseGame();
		game.put(AddedParametersTest.BURNER, AddedParametersTest.burner(true, ""));
		InvokerBridgeGuard.Result result = InvokerBridgeGuard.check("NeoForge", game, Map.of(AddedParametersTest.BURNER, game.get(AddedParametersTest.BURNER))::get,
				Map.of(AddedParametersTest.BURNER, game.get(AddedParametersTest.BURNER))::get, name -> false);
		assertTrue(result.ok() && result.bridged().isEmpty() && result.overrides().isEmpty(), result.toString());
		assertTrue(result.line(1).endsWith("no method is bridged; no game or platform class overrides what the parameters reach"), result.line(1));
	}

	private static InvokerBridgeGuard.Result guard(byte[] tnt) {
		Map<String, byte[]> game = AddedParametersTest.baseGame();
		// Not the platform's here: the sub-interface whose default reads the side, and the other interface's.
		game.remove(AddedParametersTest.SUB_EXT);
		game.remove(AddedParametersTest.OTHER_EXT);
		game.put(AddedParametersTest.BURNER, AddedParametersTest.burner(false, ""));
		game.put(TNT, tnt);
		game.put(SUB_STATE, subState());
		Map<String, byte[]> vanilla = Map.of(AddedParametersTest.BURNER, AddedParametersTest.burner(true, ""));
		return InvokerBridgeGuard.check("NeoForge", game, vanilla::get, vanilla::get, name -> false);
	}

	/**
	 * A block of the game's overriding the block extension's flammability, which answers 3, or ({@code reads}) asks the
	 * side's ordinal first.
	 */
	private static byte[] tnt(boolean reads) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TNT, null, AddedParametersTest.BLOCK, null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "flammability", AddedParametersTest.BLOCK_FLAMMABILITY, null, null);
		mv.visitCode();
		if (reads) {
			mv.visitVarInsn(Opcodes.ALOAD, 3);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, AddedParametersTest.SIDE, "ordinal", "()I", false);
			mv.visitInsn(Opcodes.POP);
		}
		mv.visitInsn(Opcodes.ICONST_3);
		mv.visitInsn(Opcodes.IRETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** A state of the game's overriding the state extension's flammability: it passes the side to the platform's hook, and answers 3. */
	private static byte[] subState() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, SUB_STATE, null, AddedParametersTest.STATE, null);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "flammability", AddedParametersTest.FLAMMABILITY, null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 2);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, AddedParametersTest.HOOKS, "ignite", AddedParametersTest.TAKES_SIDE, false);
		mv.visitInsn(Opcodes.ICONST_3);
		mv.visitInsn(Opcodes.IRETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}
}
