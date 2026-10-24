package cat.tabbie.sdk.album.model;

import java.io.IOException;

import cat.tabbie.sdk.album.repository.Describe;
import cat.tabbie.sdk.platform.Relative;
import lombok.NonNull;

/**
 * A caller-owned hold on one file of a {@link Plate}. Whatever exclusion the
 * plate applies lasts until the lease closes, so work can read a file, decide,
 * and write it without the file changing in between. A lease reads, writes, or
 * does both; {@link Exclusive} is the lease that does both.
 *
 * <p>
 * Content read through a lease includes the lease's own writes, and stops
 * being readable once the lease closes. Operations the path does not allow
 * throw a {@link Refusal} and change nothing; other I/O failures are ordinary
 * {@link IOException IOExceptions}.
 */
public sealed interface Lease extends AutoCloseable {

  /**
   * Returns the leased file.
   *
   * @return file beneath its mount
   */
  @NonNull
  Relative relative();

  /**
   * Releases the lease. Closing again does nothing; any other use afterwards
   * throws {@link IllegalStateException}.
   *
   * @throws IOException when the plate cannot release the lease
   */
  @Override
  void close() throws IOException;

  /**
   * A lease that reads its file. Plates may grant several read leases on one
   * path at once, but none while it is leased for writing.
   */
  non-sealed interface Read extends Lease {

    /**
     * Returns the file's content as it stands under this lease.
     *
     * @return content, readable until this lease closes
     * @throws Refusal               with {@link Refusal.Missing} when nothing
     *                               is at the path, or {@link Refusal.Occupied}
     *                               when something other than a file is there
     * @throws IOException           when the path cannot be inspected
     * @throws IllegalStateException when this lease is closed
     */
    @NonNull
    Describe read() throws IOException;
  }

  /**
   * A lease that changes its file. Plates grant no other lease on the path
   * until it closes.
   */
  non-sealed interface Write extends Lease {

    /**
     * Creates the file and any directories above it, writing content verified
     * against the evidence it declares. The path never holds partial content.
     *
     * @param content bytes to write
     * @throws Refusal               with {@link Refusal.Occupied} when
     *                               something is at the path or a file lies
     *                               above it, or {@link Refusal.Denied} when
     *                               the path may not be written
     * @throws IOException           when writing or verifying fails
     * @throws IllegalStateException when this lease is closed
     */
    void create(@NonNull Describe content) throws IOException;

    /**
     * Replaces the whole file with an edit of its current content, verified
     * against the evidence the edit declares. The current content stays
     * readable until the new content is in place.
     *
     * @param edit derives the new content from the current content
     * @throws Refusal               with {@link Refusal.Missing} when nothing
     *                               is at the path, {@link Refusal.Occupied}
     *                               when something other than a file is there
     *                               or a file lies above it, or
     *                               {@link Refusal.Denied} when the file may
     *                               not be written
     * @throws IOException           when reading, editing, writing or
     *                               verifying fails
     * @throws IllegalStateException when this lease is closed
     */
    void update(@NonNull Edit edit) throws IOException;

    /**
     * Deletes the file. Directories it leaves empty stay.
     *
     * @throws Refusal               as {@link #update(Edit)} refuses
     * @throws IOException           when deleting fails
     * @throws IllegalStateException when this lease is closed
     */
    void delete() throws IOException;
  }

  /**
   * A lease that reads and changes its file, for work that checks a file
   * before it writes it.
   */
  interface Exclusive extends Read, Write {
  }

  /**
   * Derives a file's new content from its current content.
   */
  @FunctionalInterface
  interface Edit {

    /**
     * Derives the new content.
     *
     * @param before current content, readable while the lease is open
     * @return new content
     * @throws IOException when the current content cannot be read
     */
    @NonNull
    Describe edit(@NonNull Describe before) throws IOException;
  }
}
