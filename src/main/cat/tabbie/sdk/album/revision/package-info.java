/**
 * Descriptions of change and the deferred work that produces them. An
 * {@link Image} describes one change to a file's existence and content, or to
 * selected lines of a text file (as {@link Chunk}s). Images compose and reduce,
 * and each has a preimage that exchanges its before and after states, all
 * without touching a filesystem. An {@link Intermediate} is unevaluated work
 * that performs its effects only when collapsed on a
 * {@link cat.tabbie.sdk.album.model.Plate Plate}.
 *
 * <p>
 * Exposing an image carries its change out on a plate, after checking that its
 * file is in the before state; steps reach their files and the stash through
 * the same plate. Both report a {@link Scrutiny}: a pass, or a failure when a
 * check ran and refused. Whether that work rehearses or acts depends only on
 * the plate: a rehearsal's plate lays an overlay over its root, and an acting
 * plate reaches the root itself.
 *
 * <p>
 * Revision history describes files, not the machine. What a Cat needs from
 * its machine is recorded in its lockfile, a text file images edit like any
 * other.
 */
package cat.tabbie.sdk.album.revision;
