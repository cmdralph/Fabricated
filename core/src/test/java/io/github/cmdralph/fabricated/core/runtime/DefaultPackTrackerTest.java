package io.github.cmdralph.fabricated.core.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultPackTrackerTest {
	@Test
	void firstOfferAddsEveryPack() {
		DefaultPackTracker.Selection selection = DefaultPackTracker.select(List.of("continuity:default", "continuity:glass_pane_culling_fix"), Set.of());
		assertEquals(List.of("continuity:default", "continuity:glass_pane_culling_fix"), selection.added());
		assertEquals(Set.of("continuity:default", "continuity:glass_pane_culling_fix"), selection.tracked());
	}

	@Test
	void trackedPackIsNotAddedAgain() {
		// The player turned it off after it was first switched on: it stays off.
		DefaultPackTracker.Selection selection = DefaultPackTracker.select(List.of("continuity:default", "other:pack"), Set.of("continuity:default"));
		assertEquals(List.of("other:pack"), selection.added());
		assertEquals(Set.of("continuity:default", "other:pack"), selection.tracked());
	}

	@Test
	void packNoLongerOfferedIsForgotten() {
		// Its mod was removed: forgotten, so it is switched on again if the mod comes back.
		DefaultPackTracker.Selection gone = DefaultPackTracker.select(List.of(), Set.of("continuity:default"));
		assertEquals(List.of(), gone.added());
		assertEquals(Set.of(), gone.tracked());
		assertEquals(List.of("continuity:default"), DefaultPackTracker.select(List.of("continuity:default"), gone.tracked()).added());
	}

	@Test
	void duplicateOfferIsAddedOnce() {
		assertEquals(List.of("a:b"), DefaultPackTracker.select(List.of("a:b", "a:b"), Set.of()).added());
	}

	@Test
	void writesVanillaNbt() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DefaultPackTracker.writeRoot(new DataOutputStream(bytes), List.of("a:b"));
		// TAG_Compound "" { TAG_List "values" of TAG_String, 1 entry: "a:b" } TAG_End, as NbtIo.write gives it.
		byte[] expected = {10, 0, 0, 9, 0, 6, 'v', 'a', 'l', 'u', 'e', 's', 8, 0, 0, 0, 1, 0, 3, 'a', ':', 'b', 0};
		assertArrayEquals(expected, bytes.toByteArray());

		ByteArrayOutputStream empty = new ByteArrayOutputStream();
		DefaultPackTracker.writeRoot(new DataOutputStream(empty), List.of());
		assertArrayEquals(new byte[] {10, 0, 0, 9, 0, 6, 'v', 'a', 'l', 'u', 'e', 's', 0, 0, 0, 0, 0, 0}, empty.toByteArray());
	}

	@Test
	void roundTripsThroughTheFile(@TempDir Path dir) throws IOException {
		Path file = dir.resolve(DefaultPackTracker.FILE);
		assertEquals(Set.of(), DefaultPackTracker.read(file), "no file yet");
		DefaultPackTracker.write(file, List.of("continuity:default", "mod:é"));
		assertEquals(Set.of("continuity:default", "mod:é"), DefaultPackTracker.read(file));
		DefaultPackTracker.write(file, List.of());
		assertEquals(Set.of(), DefaultPackTracker.read(file));
		try (var left = Files.list(file.getParent())) {
			assertEquals(List.of(file), left.toList(), "no temporary file left behind");
		}
	}

	@Test
	void readsPastOtherTags(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("tracker.dat");
		write(file, out -> {
			out.writeByte(10);
			out.writeUTF("");
			// An int, a nested compound with a byte array and a list of lists, then the values.
			out.writeByte(3);
			out.writeUTF("DataVersion");
			out.writeInt(3955);
			out.writeByte(10);
			out.writeUTF("extra");
			out.writeByte(7);
			out.writeUTF("bytes");
			out.writeInt(3);
			out.write(new byte[] {1, 2, 3});
			out.writeByte(9);
			out.writeUTF("lists");
			out.writeByte(9);
			out.writeInt(1);
			out.writeByte(8);
			out.writeInt(1);
			out.writeUTF("not a pack");
			out.writeByte(0);
			out.writeByte(9);
			out.writeUTF("values");
			out.writeByte(8);
			out.writeInt(2);
			out.writeUTF("x:y");
			out.writeUTF("z:w");
			out.writeByte(12);
			out.writeUTF("longs");
			out.writeInt(2);
			out.writeLong(1);
			out.writeLong(2);
			out.writeByte(0);
		});
		assertEquals(Set.of("x:y", "z:w"), DefaultPackTracker.read(file));
	}

	@Test
	void listOfOtherTagsHoldsNoIds() throws IOException {
		byte[] bytes = {10, 0, 0, 9, 0, 6, 'v', 'a', 'l', 'u', 'e', 's', 3, 0, 0, 0, 1, 0, 0, 0, 7, 0};
		assertEquals(Set.of(), DefaultPackTracker.readRoot(new DataInputStream(new ByteArrayInputStream(bytes))));
	}

	@Test
	void damagedFileThrows(@TempDir Path dir) throws IOException {
		Path notGzip = dir.resolve("plain.dat");
		Files.writeString(notGzip, "values=continuity:default");
		assertThrows(IOException.class, () -> DefaultPackTracker.read(notGzip));

		Path truncated = dir.resolve("truncated.dat");
		write(truncated, out -> {
			out.writeByte(10);
			out.writeUTF("");
			out.writeByte(9);
			out.writeUTF("values");
			out.writeByte(8);
			out.writeInt(2);
			out.writeUTF("only:one");
		});
		assertThrows(IOException.class, () -> DefaultPackTracker.read(truncated));

		Path huge = dir.resolve("huge.dat");
		write(huge, out -> {
			out.writeByte(10);
			out.writeUTF("");
			out.writeByte(9);
			out.writeUTF("values");
			out.writeByte(8);
			out.writeInt(Integer.MAX_VALUE);
		});
		IOException tooMany = assertThrows(IOException.class, () -> DefaultPackTracker.read(huge));
		assertTrue(tooMany.getMessage().contains("entries"), tooMany.getMessage());

		Path notCompound = dir.resolve("list.dat");
		write(notCompound, out -> {
			out.writeByte(9);
			out.writeUTF("");
		});
		assertThrows(IOException.class, () -> DefaultPackTracker.read(notCompound));
	}

	private interface Body {
		void write(DataOutputStream out) throws IOException;
	}

	private static void write(Path file, Body body) throws IOException {
		try (OutputStream raw = Files.newOutputStream(file); DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
			body.write(out);
		}
	}
}
