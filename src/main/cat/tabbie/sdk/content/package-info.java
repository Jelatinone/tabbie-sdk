/**
 * Content, and how it is installed. An {@link Artifact} is one piece of
 * provider-described content of a family: a type of it a module registers,
 * such as those of the first-party extensions. Each family places its content
 * through the {@link Install} methods contributed for it, which capture bytes
 * into the stash of the plate their context names, and describe the images
 * placing them against the context's model through an
 * {@link Installer.Install}. A {@link Descriptor} is a file content carries
 * about itself, read but never edited.
 *
 * <p>
 * Installing never writes to a Cat's files or launches processes; carrying
 * the images out belongs to core. Relations to other content name catalog
 * coordinates at any level.
 */
package cat.tabbie.sdk.content;
