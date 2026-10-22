package cat.tabbie.sdk.album.model;

import java.io.IOException;
import java.nio.file.Path;

import cat.tabbie.sdk.api.Observer;
import cat.tabbie.sdk.platform.Relative;
import cat.tabbie.sdk.platform.Command.Resolver;
import lombok.NonNull;

//TODO: Documentation
public interface Plate extends Resolver {

  /**
   * Supplies an existing execution-owned directory outside managed targets
   * for temporary files.
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
   * 
   * @param relative
   * @return
   * @throws IOException
   */
  @NonNull
  default Lease.Read read(@NonNull Relative relative) throws IOException {
    Path absolute = path(relative);
    return read(absolute);
  }

  /**
   * Leases a file for reading.
   *
   * @param path path to file
   * @return open lease, owned by the caller
   * @throws IOException when the lease cannot be taken
   */
  @NonNull
  Lease.Read read(@NonNull Path path) throws IOException;

  /**
   * Leases a file for writing.
   *
   * @param relative file beneath its mount
   * @return open lease, owned by the caller
   * @throws IOException when the lease cannot be taken
   */
  @NonNull
  default Lease.Write write(@NonNull Relative relative) throws IOException {
    Path absolute = path(relative);
    return write(absolute);
  }

  /**
   * Leases a file for writing.
   *
   * @param path path to file
   * @return open lease, owned by the caller
   * @throws IOException when the lease cannot be taken
   */
  @NonNull
  Lease.Write write(@NonNull Path path) throws IOException;

  /**
   * Leases a file for reading and writing.
   * 
   * @param <Leases> Lease descending of {@link Lease.Read Read} and
   *                 {@link Lease.Write Write}
   * @param relative file beneath its mount
   * @return open lease, owned by the caller
   * @throws IOException when the lease cannot be taken
   */
  default <Leases extends Lease.Write & Lease.Read> Leases compose(@NonNull Relative relative) throws IOException {
    Path absolute = path(relative);
    return compose(absolute);
  }

  /**
   * Leases a file for reading and writing.
   * 
   * @param <Leases> Lease descending of {@link Lease.Read Read} and
   *                 {@link Lease.Write Write}
   * @param path     path to file
   * @return open lease, owned by the caller
   * @throws IOException when the lease cannot be taken
   */
  <Leases extends Lease.Write & Lease.Read> Leases compose(@NonNull Path path) throws IOException;
}
