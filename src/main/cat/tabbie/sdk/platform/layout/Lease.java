package cat.tabbie.sdk.platform.layout;

import java.io.IOException;

import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Describe;
import lombok.NonNull;

/**
 * A caller-owned hold on one place in the tree of a
 * {@link cat.tabbie.sdk.album.model.Plate Plate}: a file a
 * {@link Node.File file node} holds, leased to read or change it, or a vacant
 * place beneath a {@link Node.Directory directory}, leased to
 * {@link Create create} a file there. Whatever exclusion the plate applies
 * lasts until the lease closes, so work can read a file, decide, and write it
 * without the file changing in between. {@link Mutual} is the lease that both
 * reads and changes its file.
 *
 * <p>
 * Every operation first checks that the permission it needs holds at its node,
 * and throws a {@link Violation} and changes nothing when it does not.
 * Operations the path does not allow throw a {@link Refusal} and change
 * nothing; other I/O failures are ordinary {@link IOException IOExceptions}.
 * Content read through a lease includes the lease's own writes, and stops
 * being readable once the lease closes. Once an operation carries out its
 * change, the plate's tree shows it.
 */
public sealed interface Lease extends AutoCloseable {

  /**
   * Returns the leased place.
   *
   * @return file beneath the root of the plate's tree
   */
  @NonNull
  Address address();

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
     * @throws Violation.Forbidden   when the plate's model does not make
     *                               the file
     *                               {@link Rule.Permission#READABLE readable}
     * @throws Refusal               with {@link Refusal.Missing} when nothing
     *                               is at the path any more, or
     *                               {@link Refusal.Occupied} when something
     *                               other than a file is there
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
     * Replaces the whole file with content verified against the evidence it
     * declares. The path never holds partial content. A file that does not
     * exist yet has no node to lease, and is created through its directory
     * instead.
     *
     * @param content bytes to write
     * @throws Violation.Forbidden   when the plate's model does not make
     *                               the file
     *                               {@link Rule.Permission#EDITABLE editable}
     * @throws Refusal               with {@link Refusal.Occupied} when
     *                               something other than a file is at the path
     *                               or a file lies above it, or
     *                               {@link Refusal.Denied} when the file may
     *                               not be written
     * @throws IOException           when writing or verifying fails
     * @throws IllegalStateException when this lease is closed
     */
    void replace(@NonNull Describe content) throws IOException;

    /**
     * Replaces the whole file with an edit of its current content, verified
     * against the evidence the edit declares. The current content stays
     * readable until the new content is in place.
     *
     * @param edit derives the new content from the current content
     * @throws Violation.Forbidden   when the plate's model does not make
     *                               the file
     *                               {@link Rule.Permission#EDITABLE editable}
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
     * Deletes the file. Directories it leaves empty stay. The file then leaves
     * the tree, though the layout may still declare its place.
     *
     * @throws Violation.Forbidden   when the plate's model does not make
     *                               the file
     *                               {@link Rule.Permission#DELETABLE deletable}
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
  interface Mutual extends Read, Write {
  }

  /**
   * A lease that creates a file at a place the tree holds nothing, as
   * {@link Node.Directory#claim(Address)} grants once it has checked the
   * tree. Plates grant no other lease on the path until it closes.
   */
  non-sealed interface Create extends Lease {

    /**
     * Creates the file and any directories above it that do not exist,
     * writing content verified against the evidence it declares. The path
     * never holds partial content. The tree then holds the file and each
     * directory made above it.
     *
     * @param content bytes to write
     * @return the created file
     * @throws Refusal               with {@link Refusal.Occupied} when
     *                               something has come to be at the path or a
     *                               file has come to lie above it since the
     *                               lease was granted, or
     *                               {@link Refusal.Denied} when the path may
     *                               not be written
     * @throws IOException           when writing or verifying fails
     * @throws IllegalStateException when this lease is closed, or has already
     *                               created its file
     */
    @NonNull
    Node.File create(@NonNull Describe content) throws IOException;
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
