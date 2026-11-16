package cat.tabbie.sdk.form;

import java.io.IOException;

import cat.tabbie.sdk.tree.Reason;
import cat.tabbie.sdk.tree.Refusal;
import cat.tabbie.sdk.tree.Shape;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * One shape read in a format, such as a file read as JSON or an archive read
 * as the entries it holds. A form is made from its shape by a {@link Factory},
 * through {@link Shape#as(Factory)}, and stays bound to it: chaining is the
 * norm, as in
 * {@code source.as(Archive::of).file("manifest.json").as(Json::of)}.
 * Making a form reads only what it needs to know the shape is in its format,
 * and refuses with a {@link Refusal} whose {@link Reason.Content} names the
 * shape and the form: {@link Reason.Content.Unreadable} when the shape is not
 * in the format, {@link Reason.Content.Malformed} when it is but is broken,
 * and {@link Reason.Content.Unsupported} when it uses what the form does not
 * read.
 *
 * <p>
 * A form reads a value from its shape, or, for an archive, is itself a
 * directory of {@link cat.tabbie.sdk.tree.Entry entries}. Forms are classes,
 * so typed forms extend general ones, as a pack's manifest extends JSON. Every
 * format lives in {@code extensions} but {@link Text}, the line view a
 * {@link cat.tabbie.sdk.revision.Revision.Diff Diff} reads through.
 */
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public abstract class Form {

  /**
   * The shape this form reads.
   */
  Shape shape;

  /**
   * Binds a form to the shape it reads.
   *
   * @param shape the shape read in this form
   */
  protected Form(@NonNull Shape shape) {
    this.shape = shape;
  }

  /**
   * Returns the shape this form reads.
   *
   * @return the shape
   */
  @NonNull
  public Shape shape() {
    return shape;
  }

  /**
   * Makes a form from a shape, such as {@code Json::of}.
   *
   * @param <Interpreted> the form's type
   */
  @FunctionalInterface
  public interface Factory<Interpreted extends Form> {

    /**
     * Reads a shape in this factory's form.
     *
     * @param shape the shape to read
     * @return the form, bound to the shape
     * @throws Refusal     with a {@link Reason.Content} when the shape is not
     *                     in the form, or is but cannot be read
     * @throws IOException when the shape cannot be read
     */
    @NonNull
    Interpreted of(@NonNull Shape shape) throws IOException;
  }
}
