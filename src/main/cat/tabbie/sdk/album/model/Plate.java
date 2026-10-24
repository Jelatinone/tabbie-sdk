package cat.tabbie.sdk.album.model;

import java.io.IOException;
import java.nio.file.Path;

import cat.tabbie.sdk.album.repository.Stash;
import cat.tabbie.sdk.api.Observer;
import cat.tabbie.sdk.platform.Command;
import cat.tabbie.sdk.platform.Relative;
import cat.tabbie.sdk.platform.Toolchain;
import lombok.NonNull;

/**
 * The context work runs in: a Cat's files, the stash, scratch space, and the
 * runtimes commands name. Every effect of an
 * {@link cat.tabbie.sdk.album.revision.Intermediate Intermediate} or
 * {@link cat.tabbie.sdk.album.revision.Image Image} goes through a plate, so the
 * same work rehearses or acts depending only on the plate it is given.
 *
 * <p>
 * Work reaches files only through {@link Lease leases}, taken by
 * {@link Relative} path. How a plate excludes other work while a lease is open
 * is its own choice. Content reaches the stash through the plate as well, since
 * a plate is a {@link Stash}: captures land alike while rehearsing and acting,
 * because retaining content changes no file of the Cat.
 *
 * <p>
 * A plate for acting reaches the real Cat. A plate for rehearsing lays an
 * overlay over it, so writes land elsewhere and later reads see them. Scratch
 * space is part of neither: it is a real directory while rehearsing and acting
 * alike, so work uses it directly.
 *
 * @apiNote A plate is caller-owned and shared by every step of the work it is
 *          given to. Implementations used by work that runs in parallel must be
 *          thread-safe. Work holds a lease on a path at most once at a time,
 *          and must not hold leases it would wait on in different orders.
 */
public interface Plate extends Command.Resolver, Stash {

  /**
   * Supplies an existing execution-owned directory for temporary files. It is
   * always on the default filesystem and never part of the Cat, and it is
   * deleted when the surrounding rehearse or act ends, so nothing may expect it
   * to exist afterwards.
   *
   * @return scratch directory
   */
  @NonNull
  Path scratch();

  /**
   * Supplies an observer of the work's steps. Every step shares it, so it
   * accepts any renderable report; an observer interested in one step's reports
   * matches on that step's record type.
   *
   * @return plate observer
   */
  @NonNull
  default Observer<? super Observer.Renderable, ? super Observer.Renderable> observer() {
    return Observer.none();
  }

  /**
   * Binds a scoped path to a physical path, only for rendering the arguments of
   * a command an outside process runs. Work reads and writes files through
   * leases, never through this path: while rehearsing, it names the real Cat
   * rather than the overlay.
   *
   * @param relative scoped path
   * @return physical path
   * @throws IllegalArgumentException when no directory is bound to the path's
   *                                  mount
   */
  @Override
  @NonNull
  Path path(@NonNull Relative relative);

  /**
   * Locates the program of a runtime meeting a requirement, for rendering a
   * command. The plate never installs one.
   *
   * @param toolchain required toolchain
   * @return the runtime's program
   * @throws IllegalStateException when no runtime meets the requirement
   */
  @Override
  @NonNull
  Path toolchain(@NonNull Toolchain toolchain);

  /**
   * Leases a file for reading.
   *
   * @param relative file beneath its mount
   * @return open lease, owned by the caller
   * @throws Refusal     with {@link Refusal.Held} when the plate does not wait
   *                     and the path is leased for writing
   * @throws IOException when the lease cannot be taken
   */
  @NonNull
  Lease.Read read(@NonNull Relative relative) throws IOException;

  /**
   * Leases a file for writing.
   *
   * @param relative file beneath its mount
   * @return open lease, owned by the caller
   * @throws Refusal     with {@link Refusal.Held} when the plate does not wait
   *                     and the path is leased
   * @throws IOException when the lease cannot be taken
   */
  @NonNull
  Lease.Write write(@NonNull Relative relative) throws IOException;

  /**
   * Leases a file for reading and writing, for work that checks a file before
   * it changes it.
   *
   * @param relative file beneath its mount
   * @return open lease, owned by the caller
   * @throws Refusal     with {@link Refusal.Held} when the plate does not wait
   *                     and the path is leased
   * @throws IOException when the lease cannot be taken
   */
  @NonNull
  Lease.Exclusive compose(@NonNull Relative relative) throws IOException;
}
