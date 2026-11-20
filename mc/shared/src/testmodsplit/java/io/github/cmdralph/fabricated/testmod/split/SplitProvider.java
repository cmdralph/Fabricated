package io.github.cmdralph.fabricated.testmod.split;

/**
 * The provider of the test mod's {@link SplitService}, in the jar the test mod nests but in a package of the test mod's
 * own (PKG-6, {@code NestedServiceTest}), as OneConfig 1.8.0's bridges are in a package its jar and its nested jar share.
 */
public final class SplitProvider implements SplitService {
}
