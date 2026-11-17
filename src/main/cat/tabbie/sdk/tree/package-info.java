/**
 * Places in a tree. A {@link Shape} is one place, a file or a directory,
 * whether or not anything is there, and its {@link State} says what is there
 * now. A shape is a {@link Node} beneath a plate's root, an {@link Entry}
 * inside an archive, or a {@link Source} its maker declares, and it is read in
 * a format through {@link Shape#as(cat.tabbie.sdk.form.Form.Factory) as}. What
 * is known about a file's bytes without reading them is its
 * {@link Describe description}, and its bytes are read and written through its
 * {@link Lease leases}. A node reports the {@link Rule rules} declared at its
 * place: the permissions that decide what may change there, and the types a
 * directory accepts. An {@link Address} is a canonical relative path, and a
 * {@link Namespace} is a named part of a Cat, such as a World: its name and its
 * head. Why a change to a place fails is the
 * {@link cat.tabbie.sdk.failure failure} model's to say.
 */
package cat.tabbie.sdk.tree;
