package io.github.cmdralph.fabricated.core.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.cmdralph.fabricated.core.testutil.TestJars;

class ClassFilesTest {
	/** It answers as the class file read into an ISO-8859-1 string does, whatever the bytes and the text. */
	@Test
	void containsAnswersAsTheFileReadIntoAStringDoes() {
		Random random = new Random(11);
		for (int round = 0; round < 2000; round++) {
			byte[] data = new byte[random.nextInt(40)];
			// A small alphabet, so texts are often found, high bytes among it.
			byte[] alphabet = {'a', 'b', '/', ';', (byte) 0xE9, (byte) 0xC3, 0};
			for (int i = 0; i < data.length; i++) data[i] = alphabet[random.nextInt(alphabet.length)];
			StringBuilder text = new StringBuilder();
			int length = random.nextInt(5);
			int from = data.length == 0 ? 0 : random.nextInt(data.length);
			for (int i = 0; i < length; i++) {
				// Mostly a piece of the data, sometimes another character, beyond ISO-8859-1 too.
				int at = from + i;
				int kind = random.nextInt(10);
				char c;
				if (at < data.length && kind < 7) c = (char) (data[at] & 0xFF);
				else if (kind == 9) c = 'ǩ';
				else c = (char) (alphabet[random.nextInt(alphabet.length)] & 0xFF);
				text.append(c);
			}
			String expected = new String(data, StandardCharsets.ISO_8859_1);
			assertEquals(expected.contains(text), ClassFiles.contains(data, text.toString()), () -> List.of(expected, text).toString());
		}
	}

	@Test
	void findsNamesInAClassFile() {
		byte[] classFile = TestJars.emptyClass("a/b/Thing", "java/lang/Object", "a/b/Marker");
		assertTrue(ClassFiles.contains(classFile, "a/b/Marker"));
		assertTrue(ClassFiles.contains(classFile, "java/lang/Object"));
		assertTrue(ClassFiles.contains(classFile, ""));
		assertFalse(ClassFiles.contains(classFile, "a/b/Other"));
		assertFalse(ClassFiles.contains(new byte[0], "a"));
	}
}
