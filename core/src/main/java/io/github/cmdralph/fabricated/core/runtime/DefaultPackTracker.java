package io.github.cmdralph.fabricated.core.runtime;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Which built-in resource packs that Fabric mods enable by default ({@code DEFAULT_ENABLED}) to switch on, as Fabric
 * API does on Fabric: a pack is added to the player's selection the first time it is offered, and only then, so a pack
 * the player turned off stays off. A pack no longer offered (its mod removed) is forgotten, and is switched on again if
 * it comes back, as upstream does.
 *
 * <p>The packs seen are kept where upstream keeps them, {@value #FILE} in the game directory: gzipped NBT, an unnamed
 * compound whose {@code "values"} list holds the pack ids as strings. An instance moved between Fabric and Fabricated
 * keeps the player's choices. The file is read and written here without Minecraft's NBT classes, whose API differs
 * between the versions Fabricated supports; the format is NBT's own (big-endian, strings in modified UTF-8), and a file
 * with other tags in it is read past them.
 */
public final class DefaultPackTracker {
	/** Upstream's file, relative to the game directory. */
	public static final String FILE = "data/fabricDefaultResourcePacks.dat";
	/** Far more pack ids, and characters in them, than any instance has: past either, the file is damaged. */
	static final int MAX_IDS = 65_536;
	static final int MAX_CHARS = 1 << 20;
	private static final int MAX_DEPTH = 512;
	private static final byte TAG_END = 0;
	private static final byte TAG_STRING = 8;
	private static final byte TAG_LIST = 9;
	private static final byte TAG_COMPOUND = 10;
	private static final String VALUES = "values";

	private DefaultPackTracker() {
	}

	/**
	 * What to do with the packs offered now.
	 *
	 * @param added   the offered packs not seen before, in offer order: to add to the player's selection
	 * @param tracked the packs to remember from now on: every pack offered now
	 */
	public record Selection(List<String> added, Set<String> tracked) {
		public Selection {
			added = List.copyOf(added);
			tracked = Set.copyOf(tracked);
		}
	}

	/**
	 * Upstream's rule: an offered pack not tracked yet is added and tracked; an offered pack already tracked is kept
	 * (whether the player kept it on or not); a tracked pack not offered now is dropped.
	 */
	public static Selection select(Collection<String> offered, Set<String> tracked) {
		Objects.requireNonNull(tracked, "tracked");
		List<String> added = new ArrayList<>();
		Set<String> now = new LinkedHashSet<>();
		for (String id : offered) {
			if (now.add(Objects.requireNonNull(id, "pack id")) && !tracked.contains(id)) added.add(id);
		}
		return new Selection(added, now);
	}

	/** The pack ids in the file, or none if there is no file. */
	public static Set<String> read(Path file) throws IOException {
		try (InputStream raw = Files.newInputStream(file);
				DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))) {
			return readRoot(in);
		} catch (NoSuchFileException e) {
			return Set.of();
		}
	}

	/** Writes the file (through a temporary file beside it, so a crash leaves the old one), making its directory. */
	public static void write(Path file, Collection<String> ids) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		Path temp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
		try {
			try (OutputStream raw = Files.newOutputStream(temp);
					DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(raw)))) {
				writeRoot(out, ids);
			}
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	/** Vanilla's {@code NbtIo.write} of {@code {values: [ids...]}}: an unnamed compound holding one named list. */
	static void writeRoot(DataOutputStream out, Collection<String> ids) throws IOException {
		out.writeByte(TAG_COMPOUND);
		out.writeUTF("");
		out.writeByte(TAG_LIST);
		out.writeUTF(VALUES);
		// Vanilla gives an empty list the element type of TAG_End.
		out.writeByte(ids.isEmpty() ? TAG_END : TAG_STRING);
		out.writeInt(ids.size());
		for (String id : ids) out.writeUTF(id);
		out.writeByte(TAG_END);
	}

	static Set<String> readRoot(DataInput in) throws IOException {
		byte type = in.readByte();
		if (type != TAG_COMPOUND) throw new IOException("not an NBT compound (tag type " + type + ")");
		in.readUTF();
		Set<String> ids = new LinkedHashSet<>();
		boolean found = false;
		while ((type = in.readByte()) != TAG_END) {
			String name = in.readUTF();
			if (type == TAG_LIST && VALUES.equals(name) && !found) {
				found = true;
				byte elements = in.readByte();
				int size = in.readInt();
				if (size < 0 || size > MAX_IDS) throw new IOException("\"values\" holds " + size + " entries");
				if (size > 0 && elements != TAG_STRING) {
					// Upstream reads only the strings of the list; a list of something else holds no pack ids.
					for (int i = 0; i < size; i++) skip(in, elements, 1);
					continue;
				}
				long chars = 0;
				for (int i = 0; i < size; i++) {
					String id = in.readUTF();
					chars += id.length();
					if (chars > MAX_CHARS) throw new IOException("\"values\" holds more than " + MAX_CHARS + " characters");
					ids.add(id);
				}
			} else {
				skip(in, type, 1);
			}
		}
		return ids;
	}

	/** Reads past one tag's payload. */
	private static void skip(DataInput in, byte type, int depth) throws IOException {
		if (depth > MAX_DEPTH) throw new IOException("NBT nested more than " + MAX_DEPTH + " deep");
		switch (type) {
			case 1 -> in.readByte();
			case 2 -> in.readShort();
			case 3 -> in.readInt();
			case 4 -> in.readLong();
			case 5 -> in.readFloat();
			case 6 -> in.readDouble();
			case 7 -> skipBytes(in, length(in), 1);
			case 8 -> in.readUTF();
			case 9 -> {
				byte elements = in.readByte();
				int size = length(in);
				for (int i = 0; i < size; i++) skip(in, elements, depth + 1);
			}
			case 10 -> {
				byte inner;
				while ((inner = in.readByte()) != TAG_END) {
					in.readUTF();
					skip(in, inner, depth + 1);
				}
			}
			case 11 -> skipBytes(in, length(in), 4);
			case 12 -> skipBytes(in, length(in), 8);
			default -> throw new IOException("unknown NBT tag type " + type);
		}
	}

	private static int length(DataInput in) throws IOException {
		int length = in.readInt();
		if (length < 0) throw new IOException("negative NBT length " + length);
		return length;
	}

	private static void skipBytes(DataInput in, int count, int size) throws IOException {
		long left = (long) count * size;
		while (left > 0) {
			int skipped = in.skipBytes((int) Math.min(left, Integer.MAX_VALUE));
			if (skipped <= 0) {
				// skipBytes may stop early without saying why: one read tells the end of the data from a pause.
				in.readByte();
				skipped = 1;
			}
			left -= skipped;
		}
	}
}
