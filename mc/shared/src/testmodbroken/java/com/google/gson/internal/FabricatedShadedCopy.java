package com.google.gson.internal;

/**
 * A class in a package of Gson, which the platform's launcher libraries provide, as a Fabric library that shades part of
 * Gson carries one (GSON-1): its copy of the package must not take the package from the platform's, where vanilla's
 * stats parser finds {@code com.google.gson.internal.Streams}.
 */
public final class FabricatedShadedCopy {
	private FabricatedShadedCopy() {
	}
}
