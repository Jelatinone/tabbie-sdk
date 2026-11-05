/**
 * The context work runs in, and what carries its effects out. A {@link Plate}
 * carries every effect of an
 * {@link cat.tabbie.sdk.revision.Intermediate Intermediate}, an
 * {@link cat.tabbie.sdk.content.Installer install} or an
 * {@link cat.tabbie.sdk.revision.Image Image}: it leases the files beneath its
 * root, captures content into its {@link Stash}, and supplies scratch space and
 * an observer. Its tree is made of {@link cat.tabbie.sdk.model.Node nodes},
 * views of what exists on the plate. A {@link Lease} holds one file for
 * reading, writing, or both, or one place for creating a file, until it
 * closes, and a {@link Refusal} says why a plate would not carry out an
 * operation the tree allows. {@link Archive} bounds and unpacks ZIP content
 * into the plate's stash.
 *
 * <p>
 * The SDK defines plates and leases but implements neither: whoever runs the
 * work supplies the plate, and so decides whether it rehearses over an overlay
 * or acts on the files beneath its root. Captured bytes are verified by digest
 * and size before they are published, and sources stay caller-owned.
 */
package cat.tabbie.sdk.plate;
