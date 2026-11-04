/**
 * Actions: what a module contributes to tabbie that the SDK chooses by
 * target, each placed on the method that provides it. Most are verbs whose
 * method carries a behaviour out; some are nouns whose method returns what
 * they name. An annotation becomes an action when it is marked
 * {@link Action}. Each method carrying one is read into a
 * {@link Contribution}, and the contributions of one action to one subject
 * are checked together as a {@link Coverage}. The
 * {@link cat.tabbie.sdk.meta.attribute attributes} on an action's method, or
 * on a type enclosing it, say which targets it applies to.
 *
 * <p>
 * The processors check a module's actions while the module compiles, so a
 * mistake fails the module's own build. Actions are kept at run time, and the
 * registry reads them when it loads the module.
 */
package cat.tabbie.sdk.meta.action;
