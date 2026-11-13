package cat.tabbie.sdk.tree;

import java.io.IOException;

import cat.tabbie.sdk.form.Form;
import cat.tabbie.sdk.form.Unreadable;
import cat.tabbie.sdk.model.Rule;
import cat.tabbie.sdk.platform.Describe;
import lombok.NonNull;

/**
 * A caller-owned hold on one file in the tree of a
 * {@link cat.tabbie.sdk.plate.Plate Plate}: the place a
 * {@link Node.File file node} names, whether or not a file is there, leased
 * to read it or to change it. Whatever exclusion the plate applies lasts until
 * the lease closes, so work can read a file, decide, and write it without the
 * file changing in between. A {@link Write write lease} also reads.
 *
 * <p>
 * Every operation first checks that the permission it needs holds at its
 * place, and throws a {@link Violation} and changes nothing when it does not.
 * Operations the path does not allow throw a {@link Refusal} and change
 * nothing; other I/O failures are ordinary {@link IOException IOExceptions}.
 * Content read through a lease includes the lease's own writes, and stops
 * being readable once the lease closes. Once an operation carries out its
 * change, the plate's tree shows it.
 *
 * <p>
 * A lease reads and writes bytes. A {@link Form} reads them as text or as a
 * JSON document.
 */
public sealed interface Lease extends AutoCloseable permits Lease.Read {

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
     * @throws Violation             with {@link Reason.Place.Denied} when the
     *                               plate's model does not make the file
     *                               {@link Rule.Permission#READABLE readable}
     * @throws Refusal               with {@link Reason.Place.Missing} when
     *                               nothing is at the path, or
     *                               {@link Reason.Place.Occupied} when
     *                               something other than a file is there
     * @throws IOException           when the path cannot be inspected
     * @throws IllegalStateException when this lease is closed
     */
    @NonNull
    Describe read() throws IOException;

    /**
     * Returns the file's content in a form, as it stands under this lease.
     *
     * @param form form to read the content in
     * @return content in the form
     * @throws Unreadable  when the content is not in the form
     * @throws IOException as {@link #read()} throws
     */
    @NonNull
    default String read(@NonNull Form form) throws IOException {
      return form.decode(read());
    }
  }

  /**
   * A lease that changes its file, and reads what it writes. Plates grant no
   * other lease on the path until it closes. The file may be absent: replacing
   * it creates it.
   */
  interface Write extends Read {

    /**
     * Replaces the whole file with content verified against the evidence it
     * declares, creating the file with any directories missing above it when
     * nothing is there. The path never holds partial content.
     *
     * @param content bytes to write
     * @throws Violation             with {@link Reason.Place.Denied} when the
     *                               plate's model does not make the file
     *                               {@link Rule.Permission#EDITABLE editable},
     *                               or, when nothing is there,
     *                               {@link Rule.Permission#CREATABLE creatable}
     * @throws Refusal               with {@link Reason.Place.Occupied} when
     *                               something other than a file is at the path,
     *                               something other than a directory lies above
     *                               it, or a name on the way differs only by
     *                               case from one beside it, or
     *                               {@link Reason.Place.Unwritable} when the
     *                               path may not be written
     * @throws IOException           when writing or verifying fails
     * @throws IllegalStateException when this lease is closed
     */
    void replace(@NonNull Describe content) throws IOException;

    /**
     * Deletes the file. Directories it leaves empty stay. The file then leaves
     * the tree, though the layout may still declare its place.
     *
     * @throws Violation             with {@link Reason.Place.Denied} when the
     *                               plate's model does not make the file
     *                               {@link Rule.Permission#DELETABLE deletable}
     * @throws Refusal               with {@link Reason.Place.Missing} when
     *                               nothing is at the path,
     *                               {@link Reason.Place.Occupied} when
     *                               something other than a file is there, or
     *                               {@link Reason.Place.Unwritable} when the
     *                               file may not be deleted
     * @throws IOException           when deleting fails
     * @throws IllegalStateException when this lease is closed
     */
    void delete() throws IOException;
  }
}
