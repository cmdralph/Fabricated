package io.github.cmdralph.fabricated.loader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.spongepowered.asm.mixin.injection.IInjectionPointContext;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.points.AfterInvoke;
import org.spongepowered.asm.mixin.injection.points.BeforeFieldAccess;
import org.spongepowered.asm.mixin.injection.points.BeforeInvoke;
import org.spongepowered.asm.mixin.injection.points.BeforeNew;
import org.spongepowered.asm.mixin.injection.points.BeforeStringInvoke;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;
import org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException;

import io.github.cmdralph.fabricated.core.mixin.ConstructorPoint;

/**
 * The injection point pre-flight names in place of a Fabric mod's {@code @Inject} point into a constructor
 * ({@link ConstructorPoint}, CORPUS-103). Forge 47's Mixin, SpongePowered's 0.8.5, takes an {@code @Inject} into a
 * constructor only at a return, and has no {@code unsafe} points; Fabric's Mixin takes one anywhere there, its points
 * being {@code unsafe} by default (Dripstone Fluid Lib registers its particles' providers in {@code Minecraft}'s
 * constructor, just after it makes the particle engine). This point finds what the point it stands for finds, with the
 * same target, ordinal, opcode, slice and arguments, moved as that point was, and lets Mixin take that anywhere in a
 * constructor, as {@code unsafe} does from 0.8.6: Mixin still takes an instance handler only after the delegate
 * constructor call ({@code Injector.checkTargetForNode}).
 */
@InjectionPoint.AtCode(namespace = ConstructorPoint.NAMESPACE, value = ConstructorPoint.CODE)
public final class ConstructorInjectionPoint extends InjectionPoint {
	private final InjectionPoint point;
	private final int shift;

	public ConstructorInjectionPoint(InjectionPointData data) {
		super(data);
		this.point = pointFor(data.get(ConstructorPoint.POINT, ""), data);
		this.shift = data.get(ConstructorPoint.SHIFT, 0);
	}

	/** Registers the point with Mixin, before it reads any Fabric mod's mixin: pre-flight names it there. */
	static void register() {
		InjectionPoint.register(ConstructorInjectionPoint.class, ConstructorPoint.NAMESPACE);
	}

	private static InjectionPoint pointFor(String name, InjectionPointData data) {
		switch (name) {
			case "INVOKE":
				return new BeforeInvoke(data);
			case "INVOKE_ASSIGN":
				return new AfterInvoke(data);
			case "INVOKE_STRING":
				return new BeforeStringInvoke(data);
			case "FIELD":
				return new BeforeFieldAccess(data);
			case "NEW":
				return new BeforeNew(data);
			default:
				throw new InvalidInjectionException(data.getMixin(), data + " names a point Fabricated's constructor point doesn't stand for: " + name);
		}
	}

	/** What the point it stands for finds, each moved by its shift, as Mixin's own shift moves them; none outside the method. */
	@Override
	public boolean find(String desc, InsnList insns, Collection<AbstractInsnNode> nodes) {
		List<AbstractInsnNode> found = new ArrayList<>();
		this.point.find(desc, insns, found);
		boolean any = false;
		for (AbstractInsnNode node : found) {
			int index = insns.indexOf(node) + this.shift;
			if (index < 0 || index >= insns.size()) continue;
			nodes.add(insns.get(index));
			any = true;
		}
		return any;
	}

	@Override
	public RestrictTargetLevel getTargetRestriction(IInjectionPointContext context) {
		return RestrictTargetLevel.ALLOW_ALL;
	}
}
