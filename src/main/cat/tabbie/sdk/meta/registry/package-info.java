/**
 * Registrations, the registry, and choosing between contributors. A type
 * marked {@link Registered} is registered as what the {@link Registrable
 * registrable kind} it is a type of says: the values of an enum, or a group of
 * them, or the type itself. While the module compiles, its processor lists
 * those types, and every type declaring a contribution, in the module's
 * {@link Registration} index, and core reads every module's index into a
 * {@link Registry}.
 *
 * <p>
 * Each action's {@link Contributions} choose between its contributors by
 * precedence, answering a {@link Contribution}: the contributors that concur,
 * or those that clash.
 */
package cat.tabbie.sdk.meta.registry;
