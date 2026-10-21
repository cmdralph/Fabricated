package io.github.cmdralph.fabricated.loader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * What Fabric mods' {@code ResourceBundle.getBundle} calls with a {@code ResourceBundle.Control} become (LOADER-3, see
 * {@code ResourceLookups}). The JDK refuses a control when called from a named module, and NeoForge runs each mod in one,
 * while Fabric runs mods in unnamed modules: LuckPerms' bootstrap failed loading its translations. This makes the
 * lookup {@code getBundle} makes with a control, without its cache: the control's candidate locales and formats, its
 * fallback locale when only the base bundle (or none) is found, each found bundle answering the keys the more specific
 * ones lack.
 */
public final class ResourceBundles {
	private ResourceBundles() {
	}

	public static ResourceBundle getBundle(String baseName, ResourceBundle.Control control, Class<?> caller) {
		return getBundle(baseName, Locale.getDefault(), caller.getClassLoader(), control);
	}

	public static ResourceBundle getBundle(String baseName, Locale locale, ResourceBundle.Control control, Class<?> caller) {
		return getBundle(baseName, locale, caller.getClassLoader(), control);
	}

	public static ResourceBundle getBundle(String baseName, Locale locale, ClassLoader loader, ResourceBundle.Control control) {
		Objects.requireNonNull(baseName, "baseName");
		Objects.requireNonNull(locale, "locale");
		Objects.requireNonNull(control, "control");
		ResourceBundle base = null;
		Exception cause = null;
		Set<Locale> tried = new HashSet<>();
		for (Locale target = locale; target != null && tried.add(target); target = control.getFallbackLocale(baseName, target)) {
			List<ResourceBundle> found = new ArrayList<>();
			Locale first = null;
			boolean onlyRoot = true;
			for (Locale candidate : control.getCandidateLocales(baseName, target)) {
				for (String format : control.getFormats(baseName)) {
					try {
						ResourceBundle bundle = control.newBundle(baseName, candidate, format, loader, false);
						if (bundle != null) {
							found.add(bundle);
							if (first == null) first = candidate;
							if (!candidate.equals(Locale.ROOT)) onlyRoot = false;
							break;
						}
					} catch (IllegalAccessException | InstantiationException | IOException | RuntimeException e) {
						cause = e;
					}
				}
			}
			if (found.isEmpty()) continue;
			ResourceBundle bundle = new Chained(found, first);
			// As the JDK: the base bundle alone is the answer only once the fallback locales found nothing better.
			if (!onlyRoot || target.equals(Locale.ROOT)) return bundle;
			if (base == null) base = bundle;
		}
		if (base != null) return base;
		MissingResourceException missing = new MissingResourceException(
				"Can't find bundle for base name " + baseName + ", locale " + locale, baseName + "_" + locale, "");
		if (cause != null) missing.initCause(cause);
		throw missing;
	}

	/**
	 * Bundles from the most specific locale to the base one: each key from the first that has it. Its locale is the most
	 * specific one's, as the JDK sets it on the bundles it loads.
	 */
	private static final class Chained extends ResourceBundle {
		private final List<ResourceBundle> bundles;
		private final Locale locale;

		Chained(List<ResourceBundle> bundles, Locale locale) {
			this.bundles = List.copyOf(bundles);
			this.locale = locale;
		}

		@Override
		public Locale getLocale() {
			return locale;
		}

		@Override
		protected Object handleGetObject(String key) {
			for (ResourceBundle bundle : bundles) {
				if (bundle.containsKey(key)) return bundle.getObject(key);
			}
			return null;
		}

		@Override
		public Enumeration<String> getKeys() {
			Set<String> keys = new LinkedHashSet<>();
			for (ResourceBundle bundle : bundles) keys.addAll(bundle.keySet());
			return Collections.enumeration(keys);
		}
	}
}
