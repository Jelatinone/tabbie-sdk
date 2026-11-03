/**
 * Registrations: how a module tells tabbie what it adds. A type marked
 * {@link Registered} is either an enum of distributions, whose every value is
 * registered, or a content family. While the module compiles, the
 * {@link Registered.Processor processor} writes its registered types into one
 * generated {@link Registration}, which the module provides as a service.
 * Core finds every module's registration with
 * {@link java.util.ServiceLoader} and merges them into a
 * {@link cat.tabbie.sdk.Registry}.
 */
package cat.tabbie.sdk.meta.registry;
