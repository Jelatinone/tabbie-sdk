package cat.tabbie.sdk.form;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import cat.tabbie.sdk.failure.Reason;
import cat.tabbie.sdk.failure.Refusal;
import cat.tabbie.sdk.tree.Shape;
import cat.tabbie.sdk.tree.Source;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * A file read as text in a charset, strictly: bytes the charset does not
 * decode are unreadable, and text it cannot encode is refused, rather than
 * either being replaced. It is the line view a
 * {@link cat.tabbie.sdk.revision.Revision.Diff Diff} reads and rewrites a file
 * through.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class Text extends Form {

  Charset charset;

  String content;

  private Text(Shape shape, Charset charset, String content) {
    super(shape);
    this.charset = charset;
    this.content = content;
  }

  /**
   * Reads a file as UTF-8 text, as most game files are.
   *
   * @param shape the file to read
   * @return the text
   * @throws Refusal     with {@link Reason.Content.Unreadable} when the shape
   *                     is not a file, or its bytes are not UTF-8 text
   * @throws IOException when the file cannot be read
   */
  public static Text of(@NonNull Shape shape) throws IOException {
    return in(StandardCharsets.UTF_8).of(shape);
  }

  /**
   * Makes text forms in a charset.
   *
   * @param charset charset the file's text is encoded in
   * @return factory reading files as text in that charset
   */
  public static Form.Factory<Text> in(@NonNull Charset charset) {
    return shape -> {
      if (!(shape instanceof Shape.File file)) {
        throw new Refusal(new Reason.Content.Unreadable(shape, Text.class));
      }
      try {
        return new Text(shape, charset, charset.newDecoder().decode(ByteBuffer.wrap(file.bytes())).toString());
      } catch (CharacterCodingException exception) {
        throw new Refusal(new Reason.Content.Unreadable(shape, Text.class), exception);
      }
    };
  }

  /**
   * Returns the charset the text is encoded in.
   *
   * @return the charset
   */
  @NonNull
  public Charset charset() {
    return charset;
  }

  /**
   * Returns the whole text.
   *
   * @return the text
   */
  @NonNull
  public String content() {
    return content;
  }

  /**
   * Writes other text in this file's charset, as a source of the file's name.
   *
   * @param content the text to write
   * @return the encoded text
   * @throws Refusal with {@link Reason.Content.Unsupported} when the charset
   *                 cannot encode the text
   */
  @NonNull
  public Source.File with(@NonNull String content) throws Refusal {
    try {
      ByteBuffer encoded = charset.newEncoder().encode(CharBuffer.wrap(content));
      byte[] bytes = new byte[encoded.remaining()];
      encoded.get(bytes);
      return Source.of(shape.name(), bytes);
    } catch (CharacterCodingException exception) {
      throw new Refusal(new Reason.Content.Unsupported(shape, Text.class,
          String.format("The text holds characters %s cannot encode.", charset.name())), exception);
    }
  }
}
