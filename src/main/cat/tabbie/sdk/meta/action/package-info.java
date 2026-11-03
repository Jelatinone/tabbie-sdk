/**
 * Actions: what a module contributes to tabbie that the SDK chooses by
 * target, each placed on the method that provides it. Most are verbs whose
 * method carries a behaviour out; some are nouns whose method returns what
 * they name. An annotation becomes an action when it is marked
 * {@link Action}, which also holds what every action shares: whether it
 * {@link Action#scoped() acts in a place}, the {@link Action.Meta record} of
 * one contribution, the {@link Action.Coverage check} of an action's
 * contributions together, and the {@link Action.Processor processor} checking
 * them. The {@link cat.tabbie.sdk.meta.attribute attributes} on an action's
 * method, or on a type enclosing it, say which targets it applies to.
 *
 * <p>
 * The processor checks a module's actions while the module compiles, so a
 * mistake fails the module's own build. Nothing reads them by reflection.
 */
package cat.tabbie.sdk.meta.action;
