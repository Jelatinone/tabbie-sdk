/**
 * Machines and processes, described rather than touched. A {@link Host} is one
 * machine with its package managers, a {@link Package} spells one system
 * package for each manager, a {@link Toolchain} is a language runtime an
 * executable needs, and a {@link Command} is a declarative program invocation.
 * An {@link Observer} receives what happens while work is carried out.
 *
 * <p>
 * Nothing in this package runs a command; executors bind these descriptions to
 * physical paths and processes.
 */
package cat.tabbie.sdk.platform;
