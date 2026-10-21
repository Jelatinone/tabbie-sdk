package cat.tabbie.sdk.album.model;

import java.io.IOException;
import java.nio.file.Path;

import cat.tabbie.sdk.api.Observer;
import cat.tabbie.sdk.platform.Relative;
import lombok.NonNull;

//TODO: Documentation
public interface Plate {

  /**
   * Supplies an existing execution-owned directory outside managed targets
   * for temporary files.
   *
   * @return scratch directory
   */
  @NonNull
  Path scratch();

  /**
   * 
   * @param relative
   * @return
   */
  @NonNull
  Path path(@NonNull Relative relative);

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
   * 
   * @param relative
   * @return
   * @throws IOException
   */
  @NonNull
  Lease.Read read(@NonNull Path path) throws IOException;

  /**
   * 
   * @param relative
   * @return
   * @throws IOException
   */
  @NonNull
  default Lease.Write write(@NonNull Relative relative) throws IOException {
    Path absolute = path(relative);
    return write(absolute);
  }

  /**
   * 
   * @param relative
   * @return
   * @throws IOException
   */
  @NonNull
  Lease.Write write(@NonNull Path path) throws IOException;
}
