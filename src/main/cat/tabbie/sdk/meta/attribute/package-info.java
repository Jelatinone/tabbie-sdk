/**
 * Attributes: information about what an annotation is placed on, unlike
 * {@link cat.tabbie.sdk.meta.action actions}. Placed on an action's method,
 * they describe the targets it applies to, its {@link Versions},
 * {@link Distributions}, {@link Environments} and {@link Platforms}, and
 * where it acts, its {@link Closures closure}. Leaving one out applies to
 * every value of it.
 *
 * <p>
 * An attribute placed on a type applies to every action within it, in nested
 * types too: the nearest declaration of each attribute wins, first the
 * method's own, then its type's, then each enclosing type's outwards. A
 * family's interface can so say once what every one of its implementations'
 * actions share.
 *
 * <p>
 * Each attribute choosing targets nests a {@code Meta} record: what the
 * attribute says, read and checked.
 * {@link cat.tabbie.sdk.meta.action.Action.Processor} reads an action's
 * attributes into their {@code Meta} records while a module compiles, and
 * core can check what several modules contribute together from the same
 * records.
 */
package cat.tabbie.sdk.meta.attribute;
