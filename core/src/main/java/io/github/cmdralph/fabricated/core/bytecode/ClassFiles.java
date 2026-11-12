package io.github.cmdralph.fabricated.core.bytecode;

/** Looks into class files' bytes without parsing them. */
public final class ClassFiles {
	private ClassFiles() {
	}

	/**
	 * Whether the class file, read byte for byte as ISO-8859-1, contains {@code text}: what
	 * {@code new String(classFile, ISO_8859_1).contains(text)} says, without copying the file into a string. A pass that
	 * rewrites some classes asks this of every class first, to parse only those naming what it rewrites.
	 */
	public static boolean contains(byte[] classFile, String text) {
		int length = text.length();
		if (length == 0) return true;
		char first = text.charAt(0);
		outer:
		for (int i = 0, last = classFile.length - length; i <= last; i++) {
			if ((classFile[i] & 0xFF) != first) continue;
			for (int j = 1; j < length; j++) {
				if ((classFile[i + j] & 0xFF) != text.charAt(j)) continue outer;
			}
			return true;
		}
		return false;
	}
}
