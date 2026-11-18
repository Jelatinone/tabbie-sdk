package cat.tabbie.sdk.revision;

import cat.tabbie.sdk.tree.Node;
import lombok.NonNull;

/**
 * What work is carried out on: the files beneath a root, such as Home or a
 * Cat, scratch space, and the observer of what happens. Every effect of an
 * {@link Intermediate} and every {@link Image} goes through a plate, so the
 * same work rehearses or acts depending only on the plate it is given.
 *
 * <p>
 * Work reaches files only through the {@link Node nodes} of the plate's
 * {@link #root() root} and {@link #scratch() scratch} trees and the
 * {@link cat.tabbie.sdk.tree.Lease leases} they grant. The plate implements
 * every node as a view of its own files, so its trees show each change as it
 * is carried out. The root's nodes report the {@link cat.tabbie.sdk.tree.Rule
 * rules} core composed for the Cat, and its leases check them again when they
 * carry a change out; scratch space allows everything. How a plate excludes
 * other work while a lease is open is its own choice.
 *
 * <p>
 * A plate for acting reaches the real files beneath its root. A plate for
 * rehearsing lays an overlay over them, so writes land elsewhere and later
 * reads see them. Scratch space is part of neither: it is a real directory
 * that lives as long as one collapse. Core implements both; the SDK's memory
 * plate comes with the testkit.
 *
 * @apiNote A plate is caller-owned and shared by every step of the work it is
 *          given to. Implementations used by work that runs in parallel must be
 *          thread-safe. Work holds a lease on a path at most once at a time,
 *          and must not hold leases it would wait on in different orders.
 */
public interface Plate {

  /**
   * Returns the directory standing for the root the work changes.
   *
   * @return the root, at the root address
   */
  @NonNull
  Node.Directory root();

  /**
   * Returns scratch space for the temporary work of one collapse, removed when
   * it ends.
   *
   * @return the scratch directory, at the root address
   */
  @NonNull
  Node.Directory scratch();

  /**
   * Returns where what happens on this plate is reported, as core was given
   * it.
   *
   * @return the observer, by default one that ignores every event
   */
  @NonNull
  default Observer observer() {
    return Observer.NONE;
  }
}
