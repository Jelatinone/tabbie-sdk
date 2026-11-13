/**
 * Places in a tree, and why a change to one is refused. A {@link Shape} is one
 * place, a file or a directory, whether or not anything is there, and its
 * {@link State} says what is there now. A {@link Node} is a place beneath a
 * plate's root, and a file's bytes are read and written through its
 * {@link Lease leases}. An {@link Address} is a canonical relative path, and a
 * {@link World} is a World as a Cat's model holds it: its name and the
 * namespace holding its content.
 *
 * <p>
 * A {@link Violation} is thrown while work describes its changes, when a tree
 * does not hold or allow what it asks for; a {@link Refusal} is thrown while a
 * plate carries an operation out, when the path or its lease does not allow
 * it. Either way, nothing has changed.
 */
package cat.tabbie.sdk.tree;
