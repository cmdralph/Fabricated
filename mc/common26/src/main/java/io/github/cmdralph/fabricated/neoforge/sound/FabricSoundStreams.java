package io.github.cmdralph.fabricated.neoforge.sound;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;

/**
 * {@code fabric-sound-api-v1}'s custom audio streams ({@code FabricSoundInstance.getAudioStream}) on NeoForge, which
 * asks the sound instance for its stream through its own {@code SoundInstance.getStream} instead of the call
 * upstream's mixin redirects. A sound instance whose class overrides Fabric's method gets its stream from it; every
 * other one keeps NeoForge's path, so NeoForge mods' streams are unaffected.
 */
public final class FabricSoundStreams {
	private static final Logger LOGGER = LoggerFactory.getLogger("Fabricated");
	private static final String FABRIC_SOUND_INSTANCE = "net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance";
	private static final @Nullable MethodHandle GET_AUDIO_STREAM = find();
	private static final ClassValue<Boolean> OVERRIDES = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
				try {
					Method method = c.getDeclaredMethod("getAudioStream", SoundBufferLibrary.class, Identifier.class, boolean.class);
					if (!Modifier.isAbstract(method.getModifiers())) return true;
				} catch (NoSuchMethodException ignored) {
					// look further up
				}
			}
			return false;
		}
	};

	private FabricSoundStreams() {
	}

	private static @Nullable MethodHandle find() {
		try {
			Class<?> fabric = Class.forName(FABRIC_SOUND_INSTANCE, false, FabricSoundStreams.class.getClassLoader());
			return MethodHandles.publicLookup().findVirtual(fabric, "getAudioStream",
							MethodType.methodType(CompletableFuture.class, SoundBufferLibrary.class, Identifier.class, boolean.class))
					.asType(MethodType.methodType(CompletableFuture.class, Object.class, SoundBufferLibrary.class, Identifier.class, boolean.class));
		} catch (ClassNotFoundException e) {
			return null; // fabric-sound-api-v1 is not installed
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.error("fabric-sound-api-v1's FabricSoundInstance has an unexpected shape; custom audio streams will not be used", e);
			return null;
		}
	}

	/** Whether {@code instance} provides its own stream through Fabric's API. */
	public static boolean providesStream(SoundInstance instance) {
		return GET_AUDIO_STREAM != null && OVERRIDES.get(instance.getClass());
	}

	@SuppressWarnings("unchecked")
	public static CompletableFuture<AudioStream> stream(SoundInstance instance, SoundBufferLibrary library, Sound sound, boolean looping) {
		try {
			return (CompletableFuture<AudioStream>) (CompletableFuture<?>) GET_AUDIO_STREAM.invokeExact((Object) instance, library, sound.getPath(), looping);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}
}
