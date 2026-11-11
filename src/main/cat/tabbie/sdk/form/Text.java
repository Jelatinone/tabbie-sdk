package cat.tabbie.sdk.form;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import cat.tabbie.sdk.platform.Describe;
import lombok.NonNull;

/**
 * Content as text in a charset, read and written strictly: bytes the
 * charset does not decode are unreadable, and text it cannot encode is
 * refused, rather than either being replaced. Text has no empty document,
 * since an empty file is just one more text.
 *
 * @param charset charset the file's text is encoded in
 */
public record Text(@NonNull Charset charset) implements Form {

  /**
   * Describes text in a charset.
   */
  public Text {
  }

  /**
   * Describes UTF-8 text, as most game files are.
   */
  public Text() {
    this(StandardCharsets.UTF_8);
  }

  @Override
  public String decode(@NonNull Describe content) throws IOException {
    try {
      return charset.newDecoder().decode(ByteBuffer.wrap(content.bytes())).toString();
    } catch (CharacterCodingException exception) {
      throw new Unreadable(content.path().canonical() + " is not " + charset.name() + " text.", exception);
    }
  }

  @Override
  public Describe encode(@NonNull Describe current, @NonNull String content) {
    try {
      ByteBuffer encoded = charset.newEncoder().encode(CharBuffer.wrap(content));
      byte[] bytes = new byte[encoded.remaining()];
      encoded.get(bytes);
      return Describe.of(current.fileName(), bytes);
    } catch (CharacterCodingException exception) {
      throw new IllegalArgumentException("Text is written only in characters its charset holds.", exception);
    }
  }

  @Override
  public Optional<Describe> empty(@NonNull String fileName) {
    return Optional.empty();
  }
}
