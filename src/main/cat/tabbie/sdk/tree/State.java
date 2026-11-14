package cat.tabbie.sdk.tree;

import lombok.NonNull;

/**
 * What is at a place at one moment: nothing, a file, a directory, or
 * something else. A state is read now and says nothing about later; a file's
 * state holds the {@link Describe description} of its bytes, so a state also
 * tells one file's content from another's without reading it.
 */
public sealed interface State permits State.Absent, State.File, State.Directory, State.Other {

  /**
   * Names what this state holds, as a message shows it, such as
   * "a directory".
   *
   * @return the phrase
   */
  @NonNull
  String phrase();

  /**
   * Whether another state holds the same: nothing, a directory or something
   * else alike, or files whose bytes nothing known tells apart.
   *
   * @param other the other state
   * @return true when they hold the same
   */
  boolean same(@NonNull State other);

  /**
   * Nothing is at the place.
   */
  record Absent() implements State {

    @Override
    public String phrase() {
      return "nothing";
    }

    @Override
    public boolean same(@NonNull State other) {
      return other instanceof Absent;
    }
  }

  /**
   * A file is at the place.
   *
   * @param describe what is known about the file's bytes
   */
  record File(@NonNull Describe describe) implements State {

    @Override
    public String phrase() {
      return "a file";
    }

    @Override
    public boolean same(@NonNull State other) {
      return other instanceof File file && describe.agrees(file.describe());
    }
  }

  /**
   * A directory is at the place.
   */
  record Directory() implements State {

    @Override
    public String phrase() {
      return "a directory";
    }

    @Override
    public boolean same(@NonNull State other) {
      return other instanceof Directory;
    }
  }

  /**
   * Something other than a file or directory is at the place, such as a
   * dangling link.
   */
  record Other() implements State {

    @Override
    public String phrase() {
      return "something other than a file or directory";
    }

    @Override
    public boolean same(@NonNull State other) {
      return other instanceof Other;
    }
  }
}
