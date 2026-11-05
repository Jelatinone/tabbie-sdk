/**
 * The model content is placed against. A {@link Layout} is an immutable model
 * of a root, such as a Cat: the directories and files it is meant to hold, at
 * addresses relative to its root, with {@link Rule rules} saying which types
 * of content a directory {@link Rule.Accepts accepts} and which
 * {@link Rule.Permission permissions} hold. It is made up rather than backed,
 * so new content is placed against it without reading any file, and it
 * describes its changes as images. A type finds the declared directories
 * {@link Layout.Accepting accepting} it, and asking for something a model or a
 * tree does not hold or allow is a {@link Violation}.
 *
 * <p>
 * A {@link World} is a World as a model holds it: the name a Cat selects it
 * by, and the directory of its namespace. {@link Node Nodes} are what exists
 * on a plate, holding no rules, and {@link Shape} is what a model and a plate's
 * tree have in common. Each distribution release declares a layout,
 * and core builds an install's model from it, with its own layer and the
 * Worlds the install applies to mounted in it.
 */
package cat.tabbie.sdk.model;
