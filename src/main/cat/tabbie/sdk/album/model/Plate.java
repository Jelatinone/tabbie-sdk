package cat.tabbie.sdk.album.model;

import java.io.IOException;
import java.nio.file.Path;
import java.util.OptionalLong;

import cat.tabbie.sdk.album.repository.Stash;
import cat.tabbie.sdk.api.Observer;
import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Command;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.Reference;
import cat.tabbie.sdk.platform.Toolchain;
import cat.tabbie.sdk.platform.layout.Lease;
import cat.tabbie.sdk.platform.layout.Node;
import lombok.NonNull;

/**
 * The context work runs in: the files beneath its root, such as Home, a Cat or
 * a World; the stash; scratch space; and the runtimes commands name. Every
 * effect of an
 * {@link cat.tabbie.sdk.album.revision.Intermediate Intermediate} or
 * {@link cat.tabbie.sdk.album.revision.Image Image} goes through a plate, so the
 * same work rehearses or acts depending only on the plate it is given.
 *
 * <p>
 * Work reaches files only through the {@link Node nodes} of the plate's
 * {@link #root() root} and {@link #scratch() scratch} trees and the
 * {@link Lease leases} they grant. The plate mounts its root's layout, with
 * core's own layer on top, and implements every node as a view of its own
 * files, so its trees show each change as it is carried out. How a plate
 * excludes other work while a lease is open is its own choice. Content reaches
 * the stash through the plate as well, since a plate is a {@link Stash}:
 * captures land alike while rehearsing and acting, because retaining content
 * changes no file beneath the root.
 *
 * <p>
 * A plate for acting reaches the real files beneath its root. A plate for
 * rehearsing lays an overlay over them, so writes land elsewhere and later
 * reads see them. Scratch space is part of neither: it is a real directory
 * while rehearsing and acting alike.
 *
 * @apiNote A plate is caller-owned and shared by every step of the work it is
 *          given to. Implementations used by work that runs in parallel must be
 *          thread-safe. Work holds a lease on a path at most once at a time,
 *          and must not hold leases it would wait on in different orders.
 */
public interface Plate extends Command.Resolver, Stash {

  /**
   * Supplies the tree of the files beneath this plate's root: the layout
   * mounted on it, with what exists there.
   *
   * @return directory standing for the root
   */
  @NonNull
  Node.Directory root();

  /**
   * Supplies the tree of an existing execution-owned directory for temporary
   * files. It is always on the default filesystem and never beneath the
   * plate's root, every permission holds throughout it, and it is deleted when
   * the surrounding rehearse or act ends, so nothing may expect it to exist
   * afterwards.
   *
   * @return directory standing for scratch space
   */
  @NonNull
  Node.Directory scratch();

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
   * Captures a source into this plate's stash, reporting to its observer as it
   * runs: each {@link Reference.Transfer} as the stash reads the source, then
   * the completed transfer, or the failure that ended it.
   *
   * @param source description of the bytes to capture
   * @return verified reference to the retained bytes
   * @throws IOException when the source cannot be read, its evidence differs,
   *                     or the bytes cannot be retained
   */
  @Override
  @NonNull
  default Reference.Captured capture(@NonNull Describe source) throws IOException {
    return capture(source, new Observer<>() {

      @Override
      public void transfer(Reference.Transfer transfer) {
        observer().transfer(transfer);
      }

      @Override
      public void verified(Reference.Captured captured) {
        observer().verified(new Reference.Transfer(source.path().canonical(), captured.size(),
            OptionalLong.of(captured.size())));
      }

      @Override
      public void failed(Exception failure) {
        observer().failed(failure);
      }
    });
  }

  /**
   * Binds an address to a physical path, only for rendering the arguments of a
   * command an outside process runs. Commands are described before any plate
   * runs them and may name files the process creates, so this binds the
   * address itself rather than a node. Work reads and writes files through
   * leases, never through this path: while rehearsing, it names the real file
   * rather than the overlay.
   *
   * @param address path beneath this plate's root
   * @return physical path
   */
  @Override
  @NonNull
  Path path(@NonNull Address address);

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
}
