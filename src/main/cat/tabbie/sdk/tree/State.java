package cat.tabbie.sdk.tree;

import lombok.NonNull;

/**
 * What is at a place at one moment: nothing, a file, a directory, or
 * something else. A state is read now and says nothing about later.
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
   * Nothing is at the place.
   */
  record Absent() implements State {

    @Override
    public String phrase() {
      return "nothing";
    }
  }

  /**
   * A file is at the place.
   */
  record File() implements State {

    @Override
    public String phrase() {
      return "a file";
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
  }
}
