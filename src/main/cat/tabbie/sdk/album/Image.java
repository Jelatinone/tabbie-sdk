package cat.tabbie.sdk.album;

import java.nio.file.Path;
import java.util.List;

import lombok.NonNull;

/**
 *
 * <h1>Image</h1>
 *
 * @param <State> Description of the underlying filesystem state
 *
 */
public sealed interface Image<State extends Image.Alteration> {

  /**
   * 
   * @return
   */
  Path path();

  /**
   *
   * @return
   */
  State before();

  /**
   *
   * @return
   */
  State after();

  /**
   * 
   * @return
   */
  Image<State> reverse();

  /**
   *
   * <h1>Alteration</h1>
   *
   * <p>
   * Describes a change to the underlying filesystem to a file's contents or
   * existence.
   * </p>
   *
   */
  sealed interface Alteration {
  }

  sealed interface File extends Alteration {

    record Absent() implements File {
    }

    record Present(@NonNull Reference reference) implements File {
    }
  }

  sealed interface Text extends Alteration {

    record Absent() implements Text {
    }

    record Present(@NonNull List<Chunk.Fragment> fragments) implements Text {
      public Present {
        Chunk.validate(fragments);
      }
    }
  }
}

/**
 *
 * <h1>Arrange</h1>
 */
record Installation(@NonNull File before, @NonNull File after, @NonNull Path path) implements Image<Image.File> {
  public Installation {
    if (before instanceof File.Absent && after instanceof File.Absent
        || before instanceof File.Present && after instanceof File.Present) {
      throw new IllegalArgumentException("Files may not both be present or absent!");
    }

  }

  @Override
  public Installation reverse() {
    return new Installation(after, before, path);
  }
}

/**
 *
 * <h1>Configure</h1>
 */
record Configuration(@NonNull Text before, @NonNull Text after, @NonNull Path path) implements Image<Image.Text> {

  public Configuration {

  }

  @Override
  public Image<Text> reverse() {
    return new Configuration(after, before, path);
  }
}
