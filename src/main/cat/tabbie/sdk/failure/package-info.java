/**
 * Why work fails. A {@link Reason} is plain data saying what went wrong, as a
 * sentence, and whether the same work may succeed when it is tried again;
 * reasons are grouped by what they are about: a place in a tree, the placement
 * of content, a change that cannot be described, a shape's content, and the
 * registry of contributions.
 *
 * <p>
 * A reason travels in one of two carriers. A {@link Violation} is thrown while
 * work describes its changes, when a tree, its rules, an image or the registry
 * does not hold or allow what it asks for; a {@link Refusal} is thrown while
 * work reads or carries an operation out, when the tree or its lease does not
 * allow it. Either way, nothing has changed, and callers deciding what to do
 * next switch over the reason, never over exception types.
 */
package cat.tabbie.sdk.failure;
