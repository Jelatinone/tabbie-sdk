package cat.tabbie.sdk.tree;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

import lombok.NonNull;

/**
 * A caller-owned hold on one {@link Shape.File file}: the place it names,
 * whether or not a file is there, leased to read it or to change it. Whatever
 * exclusion the tree applies lasts until the lease closes, so work can read a
 * file, decide, and write it without the file changing in between. A
 * {@link Write write lease} also reads.
 *
 * <p>
 * Every operation first checks that the permission it needs holds at its
 * place, and throws a {@link Violation} and changes nothing when it does not.
 * Operations the path does not allow throw a {@link Refusal} and change
 * nothing; other I/O failures are ordinary {@link IOException IOExceptions}.
 * Content read through a lease includes the lease's own writes. Closing again
 * does nothing; any other use after closing throws
 * {@link IllegalStateException}.
 */
public sealed interface Lease extends AutoCloseable permits Lease.Read {

  /**
   * Returns the leased place.
   *
   * @return file beneath the root of its tree
   */
  @NonNull
  Address address();

  /**
   * Releases the lease.
   *
   * @throws IOException when the tree cannot release the lease
   */
  @Override
  void close() throws IOException;

  /**
   * A lease that reads its file by random access, so a reader takes only the
   * bytes it needs, such as an archive's directory and one entry. Trees may
   * grant several read leases on one path at once, but none while it is leased
   * for writing.
   */
  non-sealed interface Read extends Lease {

    /**
     * Returns the size of the file's bytes as they stand under this lease.
     *
     * @return size of the bytes
     * @throws Violation             with {@link Reason.Place.Denied} when the
     *                               file is not
     *                               {@link Rule.Permission#READABLE readable}
     * @throws Refusal               with {@link Reason.Place.Missing} when
     *                               nothing is at the path, or
     *                               {@link Reason.Place.Occupied} when
     *                               something other than a file is there
     * @throws IOException           when the file cannot be read
     * @throws IllegalStateException when this lease is closed
     */
    long size() throws IOException;

    /**
     * Reads bytes from a position into a buffer, as many as are there and fit,
     * without moving any position of the lease.
     *
     * @param position offset of the first byte to read
     * @param target   buffer to read into, from its position
     * @return number of bytes read, at least one when the buffer has room and
     *         the position is before the end, or -1 at or past the end
     * @throws Violation                as {@link #size()} throws
     * @throws Refusal                  as {@link #size()} refuses
     * @throws IOException              when the file cannot be read
     * @throws IllegalArgumentException when the position is negative
     * @throws IllegalStateException    when this lease is closed
     */
    int read(long position, @NonNull ByteBuffer target) throws IOException;

    /**
     * Reads every byte, for content small enough to hold in memory.
     *
     * @return the file's bytes
     * @throws IOException           as {@link #read(long, ByteBuffer)} throws,
     *                               or when the file is too large to hold
     * @throws IllegalStateException when this lease is closed
     */
    @NonNull
    default byte[] bytes() throws IOException {
      long size = size();
      if (size > Integer.MAX_VALUE - 8) {
        throw new IOException(String.format("%s is too large to hold in memory.", address().shown()));
      }
      ByteBuffer buffer = ByteBuffer.allocate((int) size);
      int read = 0;
      while (buffer.hasRemaining() && read >= 0) {
        read = read(buffer.position(), buffer);
      }
      return buffer.position() == buffer.capacity()
          ? buffer.array()
          : Arrays.copyOf(buffer.array(), buffer.position());
    }

    /**
     * Opens a stream over the bytes from the start, reading through this
     * lease. The stream reads only while the lease is open, and closing it
     * leaves the lease open.
     *
     * @return stream at position zero, owned by the caller
     */
    @NonNull
    default InputStream stream() {
      return new InputStream() {

        private long position;

        @Override
        public int read() throws IOException {
          byte[] single = new byte[1];
          return read(single, 0, 1) < 0 ? -1 : single[0] & 0xFF;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
          if (length == 0) {
            return 0;
          }
          int read = Read.this.read(position, ByteBuffer.wrap(bytes, offset, length));
          if (read > 0) {
            position += read;
          }
          return read;
        }
      };
    }

    /**
     * Reads bytes held in memory from a position into a buffer, as
     * {@link #read(long, ByteBuffer)} does, for leases over such bytes.
     *
     * @param bytes    the bytes
     * @param position offset of the first byte to read
     * @param target   buffer to read into, from its position
     * @return number of bytes read, or -1 at or past the end
     * @throws IllegalArgumentException when the position is negative
     */
    static int copy(@NonNull byte[] bytes, long position, @NonNull ByteBuffer target) {
      if (position < 0) {
        throw new IllegalArgumentException("A read starts at a position that is not negative.");
      }
      if (position >= bytes.length) {
        return -1;
      }
      int length = (int) Math.min(target.remaining(), bytes.length - position);
      target.put(bytes, (int) position, length);
      return length;
    }
  }

  /**
   * A lease that changes its file, and reads what it writes. Trees grant no
   * other lease on the path until it closes. The file may be absent: replacing
   * it creates it.
   */
  interface Write extends Read {

    /**
     * Replaces the whole file with another file's bytes, checked against what
     * that file's {@link Shape.File#describe() description} declares. When
     * nothing is here, the file is created with any directories missing above
     * it. The path never holds partial content.
     *
     * @param source the file whose bytes to copy
     * @return what this file held before, bound to bytes the tree keeps, or
     *         empty when it was created
     * @throws Violation             with {@link Reason.Place.Denied} when the
     *                               file is not
     *                               {@link Rule.Permission#EDITABLE editable},
     *                               or, when nothing is here,
     *                               {@link Rule.Permission#CREATABLE creatable}
     * @throws Refusal               with {@link Reason.Place.Occupied} when
     *                               something other than a file is here,
     *                               something other than a directory lies
     *                               above, or a name on the way differs only by
     *                               case from one beside it,
     *                               {@link Reason.Place.Unwritable} when the
     *                               path may not be written, or
     *                               {@link Reason.Content.Mismatch} when the
     *                               bytes read do not match the source's
     *                               description
     * @throws IOException           when reading or writing fails
     * @throws IllegalStateException when this lease is closed
     */
    @NonNull
    Optional<Describe> replace(@NonNull Shape.File source) throws IOException;

    /**
     * Deletes the file. Directories it leaves empty stay.
     *
     * @return what the file held, bound to bytes the tree keeps
     * @throws Violation             with {@link Reason.Place.Denied} when the
     *                               file is not
     *                               {@link Rule.Permission#DELETABLE deletable}
     * @throws Refusal               with {@link Reason.Place.Missing} when
     *                               nothing is here,
     *                               {@link Reason.Place.Occupied} when
     *                               something other than a file is, or
     *                               {@link Reason.Place.Unwritable} when the
     *                               file may not be deleted
     * @throws IOException           when deleting fails
     * @throws IllegalStateException when this lease is closed
     */
    @NonNull
    Describe delete() throws IOException;
  }
}
