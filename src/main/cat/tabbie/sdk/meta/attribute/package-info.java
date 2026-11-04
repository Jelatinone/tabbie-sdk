/**
 * Attributes: information about what an annotation is placed on, unlike
 * {@link cat.tabbie.sdk.meta.action actions}. Placed on an action's method,
 * they describe the targets it applies to: its {@link Versions},
 * {@link Distributions}, {@link Environments} and {@link Platforms}. Leaving
 * one out applies to every value of it. Where an action acts is not an
 * attribute but the action's own value, its
 * {@link cat.tabbie.sdk.meta.action.Closure closure}.
 *
 * <p>
 * An attribute may be placed on a type too, and then applies to every action
 * within it, in nested types too: the nearest declaration of each attribute
 * wins, first the method's own, then its type's, then each enclosing type's
 * outwards. A family's interface can so say once what every one of its actions
 * share, and a method may narrow or widen it.
 *
 * <p>
 * Each attribute nests a {@code Meta} record, a {@link Condition}: what the
 * attribute says, read and checked. {@link Conditions} holds all four, and
 * applies to a {@link Conditions.Target} when each does. The processors read
 * them while a module compiles, and the registry reads the same records from
 * the same annotations when it loads the module.
 */
package cat.tabbie.sdk.meta.attribute;
