package net.fabricmc.fabric.api.transfer.v1.storage;

public interface TransferVariant<O> {
	boolean isBlank();

	O getObject();
}
