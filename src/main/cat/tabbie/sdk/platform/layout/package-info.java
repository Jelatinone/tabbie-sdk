/**
 * The declared shape of a root, such as a Cat: a tree of {@link Node nodes}
 * held by the {@link Node.Directory directory} standing for the root, each node
 * with {@link Rule rules} saying what may happen there: which types of content
 * a directory {@link Rule.Accepts accepts}, and which
 * {@link Rule.Permission permissions} it grants.
 *
 * <p>
 * Every node has its own {@link cat.tabbie.sdk.platform.Address Address}.
 * Locating an address from a node follows only declared names, never a
 * filesystem, and lands on a declared node or on content beneath a declared
 * directory, which that directory governs. A type finds the declared
 * directories {@link Node.Accepting accepting} it. Asking for something the
 * tree does not declare or allow is a {@link Violation}.
 */
package cat.tabbie.sdk.platform.layout;
