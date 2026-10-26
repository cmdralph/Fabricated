package snownee.jade.api;

/** Compile-time stand-in for Jade's plugin interface; never packaged. */
public interface IWailaPlugin {
	default void register(IWailaCommonRegistration registration) {
	}
}
