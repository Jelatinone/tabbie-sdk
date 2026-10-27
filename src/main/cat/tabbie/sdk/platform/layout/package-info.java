/**
 * The tree of a root, such as a Cat: {@link Node nodes} beneath the
 * {@link Node.Directory directory} standing for the root, each a file or a
 * directory, and each {@link Node.Declared declared} by a layout or
 * {@link Node.Present present} without one. Declared nodes hold
 * {@link Rule rules} saying what may happen there: which types of content a
 * directory {@link Rule.Accepts accepts}, and which
 * {@link Rule.Permission permissions} hold. Every other node follows the
 * permissions of the directory above it.
 *
 * <p>
 * A distribution build declares a layout, and a plate mounts it, with core's
 * own layer on top, as the tree of its root. The plate implements every node
 * as a view of its own files, so the tree shows what exists on that plate as
 * work changes it. Every node has its own
 * {@link cat.tabbie.sdk.platform.Address Address}: locating an address from a
 * node goes to exactly that node, and a type finds the declared directories
 * {@link Node.Accepting accepting} it. Asking for something the tree does not
 * hold or allow is a {@link Violation}.
 *
 * <p>
 * A {@link Lease} holds one file for reading, writing, or both until it
 * closes, and a {@link Refusal} says why a plate would not carry out an
 * operation the tree allows.
 */
package cat.tabbie.sdk.platform.layout;
