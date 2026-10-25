/**
 * The declared shape of a root, such as a Cat. A {@link Layout} holds a tree
 * of {@link Node nodes} for the root and one for the inside of a World, each
 * node with {@link Rule rules} saying what may happen there: which types of
 * content a directory {@link Rule.Accepts accepts}, and which
 * {@link Rule.Permission permissions} it grants.
 *
 * <p>
 * Finding an {@link cat.tabbie.sdk.platform.Address Address} in a layout
 * follows only declared names, never a filesystem, and lands on a
 * {@link Located location} whose declared node governs it; asking for
 * something a layout does not declare or allow is a {@link Violation}.
 */
package cat.tabbie.sdk.platform.layout;
