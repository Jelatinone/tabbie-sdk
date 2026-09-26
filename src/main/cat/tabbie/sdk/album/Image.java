package cat.tabbie.sdk.album;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.schema.Relative;
import cat.tabbie.sdk.schema.Package;
import lombok.NonNull;

/**
 * An immutable description of a change to a file's content or existence.
 * Images perform no I/O and track no filesystem metadata. The preimage
 * exchanges before and after states; checking and applying either description
 * belongs to its consumer. A replacement does not authorize overwriting
 * observed content.
 *
 * @param <State> underlying content or existence state
 */
public sealed interface Image<State extends Image.Alteration> {

  /**
   * Expected content or existence before the change
   *
   * @return expected content before
   */
  @NonNull
  State before();

  /**
   * Described content or existence after the change
   *
   * @return described content after
   */
  @NonNull
  State after();

  /**
   * Image with its before and after states exchanged
   *
   * @return image reversed
   */
  @NonNull
  Image<State> preimage();

  /**
   * Describes creation of a file from retained content, including zero bytes.
   *
   * @param path  target path
   * @param after file reference
   *
   * @return Resulting image
   */
  static Image<File> create(@NonNull Relative path, @NonNull Reference after) {
    return new Installation(new File.Absent(), new File.Present(after), path);
  }

  /**
   * Describes replacement of a file deleted and succeeded by another file
   * created.
   *
   * @param path   target path
   * @param before file reference before
   * @param after  file reference after
   *
   * @return resulting image
   */
  static Image<File> replace(@NonNull Relative path, @NonNull Reference before, @NonNull Reference after) {
    return new Installation(new File.Present(before), new File.Present(after), path);
  }

  /**
   * Describes deletion of a file whose complete content matches the reference.
   *
   * @param path   target path
   * @param before file reference
   *
   * @return resulting image
   */
  static Image<File> delete(@NonNull Relative path, @NonNull Reference before) {
    return new Installation(new File.Present(before), new File.Absent(), path);
  }

  /**
   * Describes deletion of the complete supplied text, including an empty file.
   *
   * @param path   target path
   * @param before file reference before
   *
   * @return resulting image
   */
  static Image<Text> delete(@NonNull Relative path, @NonNull String before) {
    return configure(path, new Text.Present(
        Chunk.diff(before, "").stream().map(Chunk::before).toList()), new Text.Absent());
  }

  /**
   * Describes a text change using explicit existence and fragment states.
   *
   * @param path   target path
   * @param before text before
   * @param after  text after
   *
   * @return resulting image
   */
  static Image<Text> configure(@NonNull Relative path, @NonNull Text before, @NonNull Text after) {
    return new Configuration(before, after, path);
  }

  /**
   * Describes changes to an existing file. Each chunk contributes a fragment on
   * each side; the chunks' insertion/deletion ordering is not retained.
   *
   * @param path   target path
   * @param chunks changed chunks
   *
   * @return resulting image
   */
  static Image<Text> configure(@NonNull Relative path, @NonNull List<Chunk> chunks) {
    return new Configuration(chunks, path);
  }

  /**
   * Describes text differences with three surrounding context lines.
   *
   * @param path   target path
   * @param before text before
   * @param after  text after
   *
   * @return resulting image
   */
  static Image<Text> configure(@NonNull Relative path, @NonNull String before, @NonNull String after) {
    return configure(path, Chunk.diff(before, after));
  }

  /**
   * Retains only the differing regions and their context, not complete content
   * expectations. Equal inputs produce empty fragment lists: the file must
   * exist, but no particular content is described.
   *
   * @param path         target path
   * @param before       text before
   * @param after        text after
   * @param contextLines number of context lines to keep
   *
   * @return resulting image
   */
  static Image<Text> configure(@NonNull Relative path, @NonNull String before, @NonNull String after,
      int contextLines) {
    return configure(path, Chunk.diff(before, after, contextLines));
  }

  /**
   * Describes creation of the complete supplied text; empty text is present.
   *
   * @param path  target path
   * @param after text after
   *
   * @return resulting image
   */
  static Image<Text> configure(@NonNull Relative path, @NonNull String after) {
    return configure(path, new Text.Absent(), new Text.Present(
        Chunk.diff("", after).stream().map(Chunk::after).toList()));
  }

  /**
   * Describes taking a claim on a package tabbie did not claim before.
   *
   * @param pack package recipe to claim
   *
   * @return resulting image
   */
  static Image<Claim> provision(@NonNull Package pack) {
    return new Provision(new Claim.Absent(), new Claim.Present(pack));
  }

  /**
   * Describes taking a claim on a package tabbie did not claim before.
   *
   * @param pack package recipe to claim
   *
   * @return resulting image
   */
  static Image<Claim> deprovision(@NonNull Package pack) {
    return new Provision(new Claim.Present(pack), new Claim.Absent());
  }

  /**
   * Check against a given collection's images for image correctness.
   *
   * @param images generated images
   * @return verified set of images
   * @throws IllegalArgumentException when generated images cannot be verified
   */
  static void validate(@NonNull Collection<? extends Image<?>> images) {
    Map<Relative, Image<?>> files = new LinkedHashMap<>();
    Map<String, Image<?>> packages = new LinkedHashMap<>();
    for (Image<?> image : images) {
      Image<?> clash = switch (image) {
        case Installation installation ->
          files.putIfAbsent(installation.relative(), installation);
        case Configuration configuration ->
          files.putIfAbsent(configuration.relative(), configuration);
        case Provision provision ->
          packages.putIfAbsent(provision.name(), provision);
      };
      if (clash != null) {
        throw new IllegalArgumentException("Images require unique targets: " + clash);
      }
    }
    for (Relative file : files.keySet()) {
      for (Relative parent = file.parent(); parent != null; parent = parent.parent()) {
        if (files.containsKey(parent)) {
          throw new IllegalArgumentException("A file destination may not also be used as a directory");
        }
      }
    }
  }

  /**
   * Describes the change that undoes a set.
   *
   * @param images applied images
   * @return immutable set of preimages
   */
  static Set<Image<?>> preimage(@NonNull Collection<? extends Image<?>> images) {
    return images.stream().map(Image::preimage).collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Describes the underlying filesystem state of a file's contents or existence.
   */
  sealed interface Alteration {
  }

  /**
   * Complete file-content expectations, distinguished from absence.
   */
  sealed interface File extends Alteration {

    /**
     * Assertion that no file exists at the image path.
     */
    record Absent() implements File {
    }

    /**
     * Assertion of complete retained file content, including zero bytes.
     *
     * @param reference named captured content
     */
    record Present(@NonNull Reference reference) implements File {
    }
  }

  /**
   * Text existence and selected content ranges. An empty fragment list asserts
   * existence without asserting any particular content for an existing file.
   */
  sealed interface Text extends Alteration {

    /**
     * Assertion that no text file exists at the image path.
     */
    record Absent() implements Text {
    }

    /**
     * Selected ordered ranges, rather than an implicit complete-file snapshot.
     *
     * @param fragments immutable copied ranges, possibly empty
     */
    record Present(@NonNull List<Chunk.Fragment> fragments) implements Text {

      /**
       * Copies and validates fragment order and line termination.
       *
       * @throws IllegalArgumentException when fragments overlap or have invalid
       *                                  termination
       */
      public Present {
        fragments = List.copyOf(fragments);
        Chunk.validateFragments(fragments);
      }
    }
  }

  sealed interface Claim extends Alteration {

    record Absent() implements Claim {
    }

    record Present(@NonNull Package pkg) implements Claim {
    }
  }
}

/**
 * Creates, deletes, or replaces a whole file. Both sides may be present, but
 * both sides may not be absent. Equal references describe an unchanged file.
 *
 * @param before complete content or absence expected before the change
 * @param after  complete content or absence described after the change
 * @param path   target path retained as supplied
 */
record Installation(@NonNull File before, @NonNull File after, @NonNull Relative relative)
    implements Image<Image.File> {

  /**
   * Rejects an absence-to-absence description.
   *
   * @throws IllegalArgumentException when both sides are absent
   */
  public Installation {
    if (before instanceof File.Absent && after instanceof File.Absent) {
      throw new IllegalArgumentException("Files may not both be absent!");
    }
  }

  @Override
  public Installation preimage() {
    return new Installation(after, before, relative);
  }
}

/**
 * Stores corresponding text fragments, with equal unchanged gaps on both sides.
 * Creation/deletion describes all content from line zero without gaps;
 * consumers
 * must also check actual EOF when matching complete content. Both absent is
 * invalid; both present with no fragments is an unchanged existence assertion.
 *
 * @param before text expectations before the change
 * @param after  text expectations after the change
 * @param path   target path retained as supplied
 */
record Configuration(@NonNull Text before, @NonNull Text after, @NonNull Relative relative)
    implements Image<Image.Text> {

  /**
   * Projects each authored chunk into corresponding before and after fragments.
   *
   * @param chunks ordered authored chunks
   * @param path   target path
   */
  public Configuration(@NonNull List<Chunk> chunks, @NonNull Relative path) {
    this(
        new Text.Present(chunks.stream().map(Chunk::before).toList()),
        new Text.Present(chunks.stream().map(Chunk::after).toList()),
        path);
  }

  /**
   * Checks corresponding fragments and complete creation/deletion content.
   *
   * @throws IllegalArgumentException when existence, range, or gap rules disagree
   */
  public Configuration {
    validate(before, after);
  }

  @Override
  public Image<Text> preimage() {
    return new Configuration(after, before, relative);
  }

  /**
   * Checks text expectations without reading an actual file.
   *
   * @param before text before
   * @param after  text after
   *
   * @throws IllegalArgumentException when both states are absent, corresponding
   *                                  ranges have unequal gaps, or
   *                                  creation/deletion content is incomplete
   */
  private static void validate(Text before, Text after) {
    if (before instanceof Text.Absent && after instanceof Text.Absent) {
      throw new IllegalArgumentException("Text states may not both be absent.");
    }
    if (before instanceof Text.Present(List<Chunk.Fragment> beforeText)
        && after instanceof Text.Present(List<Chunk.Fragment> afterText)) {
      Chunk.validateFragments(beforeText, afterText);
      return;
    }
    Text.Present present = before instanceof Text.Present text
        ? text
        : (Text.Present) after;
    int end = 0;
    for (Chunk.Fragment fragment : present.fragments()) {
      if (fragment.range().start() != end) {
        throw new IllegalArgumentException("Creation/deletion must describe complete text without gaps.");
      }
      end = fragment.range().end();
    }
  }
}

record Provision(@NonNull Claim before, @NonNull Claim after) implements Image<Image.Claim> {

  public Provision {
    if (before instanceof Claim.Absent && after instanceof Claim.Absent)
      throw new IllegalArgumentException("Claims may not both be absent.");
    if (before instanceof Claim.Present(Package a) && after instanceof Claim.Present(Package b)
        && !a.name().equals(b.name()))
      throw new IllegalArgumentException("A provision changes exactly one package.");
  }

  public String name() {
    return (before instanceof Claim.Present(Package p)
        ? p
        : ((Claim.Present) after).pkg()).name();
  }

  @Override
  public @NonNull Image<Claim> preimage() {
    return new Provision(after, before);
  }
}