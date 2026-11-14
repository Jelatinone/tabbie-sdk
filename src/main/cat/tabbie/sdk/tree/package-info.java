/**
 * Places in a tree, and why a change to one is refused. A {@link Shape} is one
 * place, a file or a directory, whether or not anything is there, and its
 * {@link State} says what is there now. A shape is a {@link Node} beneath a
 * plate's root, an {@link Entry} inside an archive, or a {@link Source} its
 * maker declares, and it is read in a format through
 * {@link Shape#as(cat.tabbie.sdk.form.Form.Factory) as}. What is known about a
 * file's bytes without reading them is its {@link Describe description}, and
 * its bytes are read and written through its {@link Lease leases}. An
 * {@link Address} is a canonical relative path, and a {@link Namespace} is a
 * named part of a Cat, such as a World: its name and its head.
 *
 * <p>
 * A {@link Violation} is thrown while work describes its changes, when a tree
 * does not hold or allow what it asks for; a {@link Refusal} is thrown while
 * work reads or carries an operation out, when the tree or its lease does not
 * allow it. Either carries a {@link Reason}, and either way, nothing has
 * changed.
 */
package cat.tabbie.sdk.tree;
