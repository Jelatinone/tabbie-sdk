/**
 * Content, and how it is installed. An {@link Artifact} is one piece of
 * provider-described content of a family: a type of it a module registers,
 * such as those of the first-party extensions. Each family places its content
 * through the {@link Install} methods contributed for it, which capture bytes
 * through the {@link Capture} of the {@link Work} they run in, and describe
 * the images placing them against the work's model through an
 * {@link Installer.Install}. No action's context holds a plate, node, path or
 * lease, so an action only describes images, which core exposes. A
 * {@link Descriptor} is a file content carries about itself, read but never
 * edited.
 *
 * <p>
 * Installing never writes to a Cat's files or launches processes; carrying
 * the images out belongs to core. Relations to other content name catalog
 * coordinates at any level.
 */
package cat.tabbie.sdk.content;
