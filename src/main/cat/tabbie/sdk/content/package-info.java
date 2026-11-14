/**
 * Content, and how it is installed. An {@link Artifact} is one piece of
 * provider-described content of a family: a type of it a module registers,
 * such as those of the first-party extensions. Each family places its content
 * through the {@link cat.tabbie.sdk.meta.action.Install Install} methods
 * contributed for it. Core calls an
 * install once for each place it acts, and the install
 * {@link cat.tabbie.sdk.tree.Node.Directory#receive receives} the artifact's
 * source at the {@link cat.tabbie.sdk.meta.action.Action.Context#head() head}
 * it is given, describing images. No
 * action's context holds a plate or a lease, so an action only describes
 * images, which core exposes.
 *
 * <p>
 * Installing never writes to a Cat's files or launches processes; carrying
 * the images out belongs to core. Relations to other content name catalog
 * coordinates at any level.
 */
package cat.tabbie.sdk.content;
