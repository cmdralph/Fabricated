package io.github.cmdralph.fabricated.core.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class HeldThrowsTest {
	/** A wrapper as Continuity's: state set before its wrapped model's call, reset after it. */
	private static final class Wrapper {
		boolean active;

		void emit(Runnable wrapped) {
			active = true;
			try {
				wrapped.run();
			} catch (RuntimeException e) {
				if (!HeldThrows.hold(e)) throw e;
			}
			active = false;
		}
	}

	@Test
	void aThrowInsideAWrapperIsHeldUntilTheCallReturnsAndTheWrapperFinishes() {
		Wrapper wrapper = new Wrapper();
		IllegalStateException broken = new IllegalStateException("broken model");
		Throwable outer = HeldThrows.begin();
		assertTrue(HeldThrows.holding());
		wrapper.emit(() -> {
			throw broken;
		});
		assertFalse(wrapper.active, "the wrapper reset its state after the call");
		assertSame(broken, HeldThrows.end(outer), "the call gets the throw back");
		assertFalse(HeldThrows.holding());
		assertNull(HeldThrows.end(null), "nothing is left held");
	}

	@Test
	void outsideACallNothingIsHeld() {
		Wrapper wrapper = new Wrapper();
		IllegalStateException broken = new IllegalStateException("broken model");
		assertFalse(HeldThrows.holding());
		assertFalse(HeldThrows.hold(broken));
		try {
			wrapper.emit(() -> {
				throw broken;
			});
		} catch (IllegalStateException e) {
			assertSame(broken, e, "rethrown as before");
		}
		assertTrue(wrapper.active, "unwound, as without Fabricated's call");

		Throwable outer = HeldThrows.begin();
		assertFalse(HeldThrows.hold(new StackOverflowError()), "a VirtualMachineError is the caller's to rethrow");
		assertFalse(HeldThrows.hold(null));
		assertNull(HeldThrows.end(outer));
	}

	@Test
	void theFirstThrowCountsAndTheOthersAreSuppressedInIt() {
		IllegalStateException first = new IllegalStateException("first");
		IllegalArgumentException second = new IllegalArgumentException("second");
		Throwable outer = HeldThrows.begin();
		assertTrue(HeldThrows.hold(first));
		assertTrue(HeldThrows.hold(second));
		assertTrue(HeldThrows.hold(first), "the same throw again adds nothing");
		Throwable held = HeldThrows.end(outer);
		assertSame(first, held);
		assertArrayEquals(new Throwable[] {second}, held.getSuppressed());
	}

	@Test
	void nestedCallsHoldTheirOwnThrowsAndGiveTheOuterOneBack() {
		IllegalStateException outerThrow = new IllegalStateException("outer");
		IllegalStateException innerThrow = new IllegalStateException("inner");
		Throwable none = HeldThrows.begin();
		assertTrue(HeldThrows.hold(outerThrow));
		Throwable saved = HeldThrows.begin();
		assertSame(outerThrow, saved);
		assertTrue(HeldThrows.hold(innerThrow));
		assertSame(innerThrow, HeldThrows.end(saved), "the inner call's own throw");
		assertTrue(HeldThrows.holding(), "the outer call is still under way");
		assertSame(outerThrow, HeldThrows.end(none), "the outer call's throw, untouched by the inner one");
		assertFalse(HeldThrows.holding());
		assertArrayEquals(new Throwable[0], outerThrow.getSuppressed());
	}

	@Test
	void eachThreadHoldsItsOwn() throws InterruptedException {
		Throwable outer = HeldThrows.begin();
		AtomicReference<Boolean> otherHolding = new AtomicReference<>();
		Thread other = new Thread(() -> otherHolding.set(HeldThrows.holding() || HeldThrows.hold(new IllegalStateException())));
		other.start();
		other.join();
		assertFalse(otherHolding.get(), "another thread's calls are its own");
		assertNull(HeldThrows.end(outer));
	}
}
