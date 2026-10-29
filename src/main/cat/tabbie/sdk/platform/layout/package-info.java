/**
 * The {@link Shape} of a root, such as a Cat, in two systems: what it is meant
 * to hold, and what it does hold. A {@link Layout} is an immutable model:
 * directories and files at addresses relative to its root, with
 * {@link Rule rules} saying which types of content a directory
 * {@link Rule.Accepts accepts} and which {@link Rule.Permission permissions}
 * hold. It is made up rather than backed, so new content is placed against
 * it without reading any file. {@link Node Nodes} are what exists on a plate,
 * holding no rules, and changes to existing content are described from them.
 * Both describe their changes as images.
 *
 * <p>
 * Each distribution release declares a layout, and core builds an install's
 * model from it, with its own layer and the Worlds the install applies to
 * mounted in it. The plate implements every node as a view of its own files,
 * so its tree shows what exists on that plate as work changes it, and it
 * checks its model's rules when it carries a change out. Every place has its
 * own {@link cat.tabbie.sdk.platform.Address Address}: locating an address
 * goes to exactly that place, and a type finds the declared directories
 * {@link Layout.Accepting accepting} it. Asking for something a model or a
 * tree does not hold or allow is a {@link Violation}.
 *
 * <p>
 * A {@link Lease} holds one file for reading, writing, or both, or one place
 * for creating a file, until it closes, and a {@link Refusal} says why a plate
 * would not carry out an operation the tree allows.
 */
package cat.tabbie.sdk.platform.layout;
