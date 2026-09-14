package cat.tabbie.sdk;

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

  /**
   *
   * <h1>Creation</h1>
   *
   * <p>
   * Describes an alteration to the underlying filesystem that resulted in the
   * creation of a
   * </p>
   *
   */
  sealed interface Creation extends Alteration {

    record Absent() implements Creation {
    }

    record Present() implements Creation {
    }
  }

  sealed interface Deletion extends Alteration {

    record Absent() implements Deletion {
    }

    record Present() implements Deletion {
    }
  }

  sealed interface Modification extends Alteration {

    record Absent() implements Modification {
    }

    record Present() implements Modification {
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
}
