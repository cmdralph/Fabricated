package io.github.cmdralph.fabricated.loader;

import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * One jar of a mod file Fabricated hands NeoForge, with the files of it the mod's module has ({@link Fml#addModFile}).
 *
 * @param path  the jar
 * @param keeps whether the module has a file, by its path relative to the jar's root; null for all of them
 */
record FilteredPath(Path path, Predicate<String> keeps) {
}
