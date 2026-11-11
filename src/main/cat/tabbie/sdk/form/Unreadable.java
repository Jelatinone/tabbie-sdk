package cat.tabbie.sdk.form;

import java.io.IOException;
import java.io.Serial;

import lombok.NonNull;

/**
 * Content that is not in the form it was read in, such as text that is not
 * a JSON document, or a document not shaped as its reader requires.
 */
public final class Unreadable extends IOException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Describes content not in its form.
   *
   * @param message what the content is not
   * @param cause   what reading it threw, or null
   */
  public Unreadable(@NonNull String message, Throwable cause) {
    super(message, cause);
  }
}
