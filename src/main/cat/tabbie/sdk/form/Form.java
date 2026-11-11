package cat.tabbie.sdk.form;

import java.io.IOException;
import java.util.Optional;

import cat.tabbie.sdk.platform.Describe;
import lombok.NonNull;

/**
 * A form a file's content is read and written in, other than bytes: plain
 * text in a charset, or JSON. Its content is text a configuration edits,
 * either the file's own text or a view the form keeps well formed, written
 * back into the file as it was, so whatever is edited through it, what it
 * writes reads back again. Forms are views over any lease, so plates
 * implement none of them, and they read any {@link Describe}, such as an
 * entry of an archive, alike.
 *
 * <p>
 * A form is plain data: a configuration records the form it edits its file
 * in, so history recorded once replays anywhere. Another format, such as the
 * YAML or TOML some configuration files use, becomes a form of its own once
 * some content needs it.
 */
public sealed interface Form permits Text, Json {

  /**
   * Reads content in this form.
   *
   * @param content bytes to read
   * @return content in this form, as text a configuration edits
   * @throws Unreadable  when the bytes are not content of this form
   * @throws IOException when the bytes cannot be read
   */
  @NonNull
  String decode(@NonNull Describe content) throws IOException;

  /**
   * Writes content in this form back into a file as it was, as the bytes the
   * file then holds, known by the same name.
   *
   * @param current the file as it was, which the content was read from
   * @param content content in this form
   * @return reusable description of the bytes
   * @throws Unreadable               when the file as it was is not of this
   *                                  form
   * @throws IOException              when the file as it was cannot be read
   * @throws IllegalArgumentException when the content cannot be written in
   *                                  this form
   */
  @NonNull
  Describe encode(@NonNull Describe current, @NonNull String content) throws IOException;

  /**
   * Returns an empty document of this form: what a file of this form holds
   * when nothing is in it yet, and so what one may be created from.
   *
   * @param fileName single filename the document is known by
   * @return the empty document, or empty when this form has none
   * @throws IllegalArgumentException when the filename is not a single
   *                                  filename
   */
  @NonNull
  Optional<Describe> empty(@NonNull String fileName);
}
