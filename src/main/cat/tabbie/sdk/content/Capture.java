package cat.tabbie.sdk.content;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import cat.tabbie.sdk.model.Layout;
import cat.tabbie.sdk.model.Violation;
import cat.tabbie.sdk.plate.Archive;
import cat.tabbie.sdk.plate.Plate;
import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Describe;
import cat.tabbie.sdk.platform.Observer;
import cat.tabbie.sdk.platform.Reference;
import cat.tabbie.sdk.revision.Image;
import lombok.NonNull;

/**
 * Everything an action may do with bytes: capture them into the stash of the
 * work it runs in, inspect and unpack archives within core's limits, describe
 * what was captured, and report as it goes. An action's context holds a
 * capture and never a {@link Plate}, a node, a path or a lease, so an action
 * reaches no file of the Cat: it only describes images, which core exposes.
 *
 * <p>
 * Capturing changes no file beneath the Cat's root, so it runs alike while
 * rehearsing and acting; the scratch space unpacking uses is core's, and never
 * a Cat's files.
 */
public interface Capture {

  /**
   * Creates the capture of work running on a plate: it captures into the
   * plate's stash, unpacks in its scratch space, reports to its observer, and
   * processes archives within core's limits. Core creates it; the plate stays
   * out of the action's reach.
   *
   * @param plate  plate the work runs on
   * @param limits limits archives are processed within
   * @return the capture
   */
  static Capture of(@NonNull Plate plate, @NonNull Archive limits) {
    return new PlateCapture(plate, limits);
  }

  /**
   * Captures a source into the stash of the work, reporting to its observer as
   * it runs.
   *
   * @param source description of the bytes to capture
   * @return verified reference to the retained bytes
   * @throws IOException when the source cannot be read, its evidence differs,
   *                     or the bytes cannot be retained
   */
  @NonNull
  Reference.Captured capture(@NonNull Describe source) throws IOException;

  /**
   * Captures an archive, then inspects its central directory within core's
   * limits.
   *
   * @param archive archive to inspect, named by its source
   * @return the archive's file entries
   * @throws IOException when the archive cannot be captured or is refused
   */
  @NonNull
  List<Archive.Entry> inspect(@NonNull Describe archive) throws IOException;

  /**
   * Unpacks an archive within core's limits into several destinations beneath
   * a directory of a model, inspecting it once and capturing each of its files
   * once, however many destinations receive it.
   *
   * @param archive      archive to unpack, named by its source
   * @param directory    directory of a model to unpack beneath
   * @param destinations addresses of the extraction directories relative to
   *                     it, each receiving every file
   * @return immutable images creating the archive's files in every
   *         destination
   * @throws IOException when the archive cannot be captured or is refused
   * @throws Violation   when the model does not allow a file where the archive
   *                     puts it
   */
  @NonNull
  Set<Image<?>> unpack(
      @NonNull Describe archive,
      @NonNull Layout.Directory directory,
      @NonNull Set<Address> destinations) throws IOException;

  /**
   * Unpacks the inspected entries of an archive that lie beneath a folder of
   * it, each at its path relative to that folder, into several destinations,
   * as {@link #unpack(Describe, Layout.Directory, Set)} does with every entry.
   * Work that reads an archive's entries before unpacking it unpacks them this
   * way, without capturing the archive again.
   *
   * @param entries      file entries of one archive, as
   *                     {@link #inspect(Describe)} found them
   * @param from         folder of the archive to unpack from, the root address
   *                     for the whole archive
   * @param directory    directory of a model to unpack beneath
   * @param destinations addresses of the extraction directories relative to
   *                     it, each receiving every file unpacked
   * @return immutable images creating the files in every destination
   * @throws IOException              when the archive cannot be reopened or is
   *                                  refused
   * @throws Violation                when the model does not allow a file
   *                                  where it is unpacked
   * @throws IllegalArgumentException when no entry lies beneath the folder
   */
  @NonNull
  Set<Image<?>> unpack(
      @NonNull List<Archive.Entry> entries,
      @NonNull Address from,
      @NonNull Layout.Directory directory,
      @NonNull Set<Address> destinations) throws IOException;

  /**
   * Describes bytes the work captured, by reference. Describing performs no
   * I/O; opening the result yields exactly the captured bytes.
   *
   * @param captured reference a capture of the work returned
   * @return reusable description of the retained bytes
   */
  @NonNull
  Describe retained(@NonNull Reference.Captured captured);

  /**
   * Returns the observer of the work, which every step shares, so an action
   * can report its own progress beside the captures it makes.
   *
   * @return observer of the work
   */
  @NonNull
  Observer<? super Observer.Renderable, ? super Observer.Renderable> observer();
}

/**
 * The capture of work running on a plate, which it keeps out of reach: no
 * module but the SDK can name this type.
 *
 * @param plate  plate the work runs on
 * @param limits limits archives are processed within
 */
record PlateCapture(@NonNull Plate plate, @NonNull Archive limits) implements Capture {

  @Override
  public Reference.Captured capture(@NonNull Describe source) throws IOException {
    return plate.capture(source);
  }

  @Override
  public List<Archive.Entry> inspect(@NonNull Describe archive) throws IOException {
    return limits.inspect(archive, plate);
  }

  @Override
  public Set<Image<?>> unpack(
      @NonNull Describe archive,
      @NonNull Layout.Directory directory,
      @NonNull Set<Address> destinations) throws IOException {
    return limits.unpack(archive, directory, destinations, plate);
  }

  @Override
  public Set<Image<?>> unpack(
      @NonNull List<Archive.Entry> entries,
      @NonNull Address from,
      @NonNull Layout.Directory directory,
      @NonNull Set<Address> destinations) throws IOException {
    return limits.unpack(entries, from, directory, destinations, plate);
  }

  @Override
  public Describe retained(@NonNull Reference.Captured captured) {
    return plate.retained(captured);
  }

  @Override
  public Observer<? super Observer.Renderable, ? super Observer.Renderable> observer() {
    return plate.observer();
  }
}
