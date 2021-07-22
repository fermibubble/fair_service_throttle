package io.fermibubble.fst.time;

/**
 * TimeProvider provides a simple wrapper around {@link System#nanoTime()}, making it easier to
 * write deterministic unit tests
 */
public interface TimeProvider {

    /**
     * Default TimeProvider, backed by {@link System#nanoTime()}
     */
    TimeProvider DEFAULT = new SystemTimeProvider();

    /**
     * Get the current system time in nanoseconds
     * @return System time in nanoseconds
     */
   long nanoTime();

    /**
     * Implementation of default time provider
     */
   class SystemTimeProvider implements TimeProvider {
       @Override
       public long nanoTime() {
           return System.nanoTime();
       }
   }
}
