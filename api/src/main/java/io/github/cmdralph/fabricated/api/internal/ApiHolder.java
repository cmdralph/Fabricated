package io.github.cmdralph.fabricated.api.internal;

import io.github.cmdralph.fabricated.api.FabricatedApi;

/** Not API: where Fabricated publishes its implementation. Only the first installation counts. */
public final class ApiHolder {
	private static volatile FabricatedApi instance;

	private ApiHolder() {
	}

	public static FabricatedApi get() {
		return instance;
	}

	/** @throws IllegalStateException when an implementation is already installed */
	public static synchronized void install(FabricatedApi api) {
		if (instance != null) throw new IllegalStateException("Fabricated's API is already installed");
		instance = api;
	}
}
