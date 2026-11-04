/**
 * Registrations: how a module tells tabbie what it adds. A type marked
 * {@link Registered} is an enum of distributions, whose every value is
 * registered, an abstract type of distributions registered as a group, or a
 * content family. While the module compiles, its processor lists those types,
 * and every type declaring a contribution, in one generated
 * {@link Registration}, which the module provides as a service. Core finds
 * every module's registration with {@link java.util.ServiceLoader} and reads
 * them into a {@link cat.tabbie.sdk.Registry}.
 */
package cat.tabbie.sdk.meta.registry;
