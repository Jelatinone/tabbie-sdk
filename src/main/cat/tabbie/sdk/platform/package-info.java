/**
 * Bytes, paths, and processes, described rather than touched. An
 * {@link Address} is a canonical relative path, such as a file in a Cat.
 * {@link Describe} is the source role, a reusable description of how to obtain
 * bytes and what they are called, and a {@link Reference} is the identity of
 * bytes by content alone, expected or verified. A {@link Host} is one machine
 * with its package managers, a {@link Package} spells one system package for
 * each manager, a {@link Toolchain} is a language runtime an executable needs,
 * and a {@link Command} is a declarative program invocation. An
 * {@link Observer} receives progress and outcome callbacks from observed work.
 *
 * <p>
 * Nothing in this package runs a command; executors bind these descriptions to
 * physical paths and processes. Describing content performs no I/O, and
 * sources stay caller-owned.
 */
package cat.tabbie.sdk.platform;
