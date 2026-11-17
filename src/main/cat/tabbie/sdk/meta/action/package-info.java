/**
 * Actions: what a module contributes to tabbie that the SDK chooses by
 * target, each placed on the method that provides it. Most are verbs whose
 * method carries a behaviour out; some are nouns whose method returns what
 * they name. An annotation becomes an action when it is marked
 * {@link Action}. Each method carrying one is read into a
 * {@link Contributor}, and the contributors of one action to one subject, by
 * one key, are checked together as a {@link Coverage}. An action's
 * {@link Cardinality} says how many of its contributors act for one subject at
 * one target. The {@link cat.tabbie.sdk.meta.attribute attributes} on an
 * action's method, or on a type enclosing it, say which targets it applies to,
 * and the {@link cat.tabbie.sdk.meta.registry.Registry registry} chooses one
 * for a subject at a target. Every action's context extends {@link Action.Context}, which
 * gives the head it starts from. {@link Place} says where what a Cat accepts
 * goes, {@link Base} gives the Cat's root its permissions, and
 * {@link Install} installs content where its family is placed. Places and
 * bases act on types, so their contributions are static and take only their
 * context.
 *
 * <p>
 * The processors check a module's actions while the module compiles, so a
 * mistake fails the module's own build. Actions are kept at run time, and the
 * registry reads them when it loads the module.
 */
package cat.tabbie.sdk.meta.action;
