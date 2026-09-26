package cat.tabbie.sdk.album.repository;

import java.io.IOException;

import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.api.Observer;
import lombok.NonNull;

/**
 * A retention backend capable of capturing an arbitrary described source
 * under that source's own identity, rather than this backend's own.
 */
public interface Extract {

  /**
   * Checks whether content matching the given description is already
   * retained, without acquiring it.
   *
   * @param source expected identity to check for
   * @return whether matching content is already retained
   * @throws IOException when availability cannot be checked
   */
  default boolean exists(Reference.Pending source) throws IOException {
    return false;
  }

  /**
   * Captures a described source, verifying and retaining its bytes under
   * its own identity. The default opens the source only when the returned
   * intermediate is collapsed.
   *
   * @param source   reusable description of the bytes to capture
   * @param observer transfer callbacks
   * @return verified reference to retained bytes
   */
  @NonNull
  default Intermediate<Reference.Captured> capture(@NonNull Describe source,
      @NonNull Observer<? super Reference.Captured, ? super Store.Transfer> observer) {
    return new Store.Extraction(source, observer);
  }

  /**
   * Captures a described source, verifying and retaining its bytes under
   * its own identity. The default opens the source only when the returned
   * intermediate is collapsed.
   *
   * @param source reusable description of the bytes to capture
   * @return verified reference to retained bytes
   */
  @NonNull
  default Intermediate<Reference.Captured> capture(@NonNull Describe source) {
    return capture(source, Observer.none());
  }
}
