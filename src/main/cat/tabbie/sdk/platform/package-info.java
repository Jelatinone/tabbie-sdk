/**
 * Machines, paths, and processes, described rather than touched.
 * {@link Relative} is a logical path beneath an installation or world mount.
 * {@link Platform} is a kernel and architecture, and a {@link Host} is one
 * machine with its package managers. A {@link Package} spells one system
 * package for each manager, a {@link Toolchain} is a language runtime an
 * executable needs, and a {@link Command} is a declarative program invocation.
 *
 * <p>
 * Nothing in this package inspects a machine or runs a command; executors
 * bind these descriptions to physical paths and processes.
 */
package cat.tabbie.sdk.platform;
