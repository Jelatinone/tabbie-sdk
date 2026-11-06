/**
 * Attributes: information about what an annotation is placed on, unlike
 * {@link cat.tabbie.sdk.meta.action actions}. Placed on an action's method,
 * {@link Applies} says which targets it applies to: regions of
 * distributions, versions, environments and platforms. Leaving every
 * declaration out applies to every target. Where an action acts is not an
 * attribute but the action's own value, its
 * {@link cat.tabbie.sdk.meta.action.Closure closure}.
 *
 * <p>
 * A declaration may be placed on a type too, and then applies to every
 * action within it, in nested types too. Declarations nest element by
 * element: the method's own within its type's, within each enclosing type's
 * outwards, each taking what it leaves out from the one enclosing it. A
 * family's interface can so say once what every one of its actions share,
 * and a method narrow or widen any element of it.
 *
 * <p>
 * Each declaration reads into a {@link Region}, and a contribution's regions
 * together into its {@link Conditions}, which apply to a
 * {@link Conditions.Target} when one region does. The processors read them
 * while a module compiles, and the registry reads the same records from the
 * same annotations when it loads the module.
 */
package cat.tabbie.sdk.meta.attribute;
