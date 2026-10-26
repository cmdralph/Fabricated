package io.github.cmdralph.fabricated.neoforge.transfer;

import net.fabricmc.fabric.api.transfer.v1.storage.TransferVariant;
import net.minecraft.core.component.DataComponentPatch;

/** What differs on 1.21.11 for {@link TransferBridge}. */
final class TransferCompat {
	private TransferCompat() {
	}

	static DataComponentPatch patch(TransferVariant<?> variant) {
		return variant.getComponents();
	}
}
