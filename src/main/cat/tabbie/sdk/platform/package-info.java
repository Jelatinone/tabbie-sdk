/**
 * Machines and processes, described rather than touched. A {@link Host} is one
 * machine with its package managers, a {@link Package} spells one system
 * package for each manager, a {@link Toolchain} is a language runtime an
 * executable needs, and an {@link Execution} is a declarative run of a program.
 *
 * <p>
 * Nothing in this package runs a program; executors bind these descriptions to
 * physical paths and processes.
 */
package cat.tabbie.sdk.platform;
