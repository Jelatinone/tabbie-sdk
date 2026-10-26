/**
 * The shape of a root, such as a Cat: a tree of {@link Node nodes} held by the
 * {@link Node.Directory directory} standing for the root, each node with
 * {@link Rule rules} saying what may happen there: which types of content a
 * directory {@link Rule.Accepts accepts}, and which
 * {@link Rule.Permission permissions} it grants.
 *
 * <p>
 * A declaration is such a tree, saying what goes where. A plate keeps a live
 * one: the declaration with the files that exist beneath the plate's root
 * {@link Node.Directory#add(cat.tabbie.sdk.platform.Address) added}. Every node
 * has its own {@link cat.tabbie.sdk.platform.Address Address}, and locating an
 * address from a node follows only the tree's names, never a filesystem. A type
 * finds the directories {@link Node.Accepting accepting} it. Asking for
 * something the tree does not hold or allow is a {@link Violation}.
 */
package cat.tabbie.sdk.platform.layout;
