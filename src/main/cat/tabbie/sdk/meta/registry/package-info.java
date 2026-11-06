/**
 * Registrations, and choosing between contributors. A type marked
 * {@link Registered} is registered as what the {@link Registrable registrable
 * kind} it is a type of says: the values of an enum, or a group of them, or
 * the type itself. While the module compiles, its processor lists those types,
 * and every type declaring a contribution, in one generated
 * {@link Registration}, which the module provides as a service. Core finds
 * every module's registration with {@link java.util.ServiceLoader} and reads
 * them into a {@link cat.tabbie.sdk.Registry}.
 *
 * <p>
 * Each action's {@link Contributions} choose between its contributors by
 * precedence, answering a {@link Contribution}: the contributors that concur,
 * or those that clash.
 */
package cat.tabbie.sdk.meta.registry;
