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

  sealed interface Creation extends Alteration {

    record Absent() implements Creation {
    }

    record Present(@NonNull Snapshot.Reference reference) implements Creation {
    }
  }

  sealed interface Deletion extends Alteration {

    record Absent(@NonNull Snapshot.Reference reference) implements Deletion {
    }

    record Present() implements Deletion {
    }
  }

  sealed interface Modification extends Alteration {

    record Absent() implements Modification {
    }

    record Present(List<Chunk.Fragment> fragments, Snapshot.Metadata metadata) implements Modification {
    }
  }
}

/**
 *
 * <h1>Arrange</h1>
 */
record Arrange() implements Image<Image.Creation> {

  @Override
  public Creation before() {
    throw new UnsupportedOperationException("Unimplemented method 'before'");
  }

  @Override
  public Creation after() {
    throw new UnsupportedOperationException("Unimplemented method 'after'");
  }

  @Override
  public Path path() {
    throw new UnsupportedOperationException("Unimplemented method 'path'");
  }
}

/**
 *
 * <h1>Configure</h1>
 */
record Configure() implements Image<Image.Modification> {

  @Override
  public Modification before() {
    throw new UnsupportedOperationException("Unimplemented method 'before'");
  }

  @Override
  public Modification after() {
    throw new UnsupportedOperationException("Unimplemented method 'after'");
  }

  @Override
  public Path path() {
    throw new UnsupportedOperationException("Unimplemented method 'path'");
  }
}
