/**
 * Descriptions of change, and the deferred work that carries them out. An
 * {@link Image} describes one change to a file beneath a root: its existence,
 * or its content by a {@link Revision}, a {@link Revision.Swap swap} of the
 * whole content or a {@link Revision.Diff diff} of positioned {@link Hunk}s.
 * Images compose and reduce, and each has a preimage that undoes it, all
 * without touching a filesystem. An {@link Intermediate} is unevaluated work
 * that performs its effects only when collapsed on a {@link Plate}.
 *
 * <p>
 * Exposing an image carries its change out on a plate, after checking that its
 * file still holds what the image was described from; steps reach their files
 * through the same plate. Both report a {@link Scrutiny}: a pass, or a failure
 * with the reason a check gave. Whether that work rehearses or acts depends
 * only on the plate: a rehearsal's plate lays an overlay over its root, and an
 * acting plate reaches the root itself.
 *
 * <p>
 * Revision history describes files, not the machine. What a Cat needs from
 * its machine is recorded in its lockfile, a text file images edit like any
 * other.
 */
package cat.tabbie.sdk.revision;
