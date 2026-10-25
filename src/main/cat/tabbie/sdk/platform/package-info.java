/**
 * Machines, paths, and processes, described rather than touched. Paths are
 * {@link cat.tabbie.sdk.platform.layout.Address addresses} beneath a Cat, whose
 * declared shape is its {@link cat.tabbie.sdk.platform.layout.Layout Layout}.
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
