package io.github.cmdralph.fabricated.testmod;

/**
 * A service of {@link NestedServiceTest} that the test mod's own file declares with a comment after its provider's name,
 * as ServiceLoader reads it on Fabric's class path.
 */
public interface CommentedService {
}
