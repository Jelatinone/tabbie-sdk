package cat.tabbie.sdk.platform.layout;

import java.io.IOException;
import java.io.Serial;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cat.tabbie.sdk.platform.Address;
import cat.tabbie.sdk.platform.Describe;
import lombok.NonNull;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonPointer;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A caller-owned hold on one place in the tree of a
 * {@link cat.tabbie.sdk.album.model.Plate Plate}: a file a
 * {@link Node.File file node} holds, leased to read or change it, or a vacant
 * place beneath a {@link Node.Directory directory}, leased to
 * {@link Create create} a file there. Whatever exclusion the plate applies
 * lasts until the lease closes, so work can read a file, decide, and write it
 * without the file changing in between. {@link Mutual} is the lease that both
 * reads and changes its file.
 *
 * <p>
 * Every operation first checks that the permission it needs holds at its node,
 * and throws a {@link Violation} and changes nothing when it does not.
 * Operations the path does not allow throw a {@link Refusal} and change
 * nothing; other I/O failures are ordinary {@link IOException IOExceptions}.
 * Content read through a lease includes the lease's own writes, and stops
 * being readable once the lease closes. Once an operation carries out its
 * change, the plate's tree shows it.
 *
 * <p>
 * A lease reads and writes bytes. A {@link Form} reads and writes them as text
 * or as a JSON document, through any lease.
 */
public sealed interface Lease extends AutoCloseable {

  /**
   * Returns the leased place.
   *
   * @return file beneath the root of the plate's tree
   */
  @NonNull
  Address address();

  /**
   * Releases the lease. Closing again does nothing; any other use afterwards
   * throws {@link IllegalStateException}.
   *
   * @throws IOException when the plate cannot release the lease
   */
  @Override
  void close() throws IOException;

  /**
   * A lease that reads its file. Plates may grant several read leases on one
   * path at once, but none while it is leased for writing.
   */
  non-sealed interface Read extends Lease {

    /**
     * Returns the file's content as it stands under this lease.
     *
     * @return content, readable until this lease closes
     * @throws Violation.Forbidden   when the plate's model does not make
     *                               the file
     *                               {@link Rule.Permission#READABLE readable}
     * @throws Refusal               with {@link Refusal.Missing} when nothing
     *                               is at the path any more, or
     *                               {@link Refusal.Occupied} when something
     *                               other than a file is there
     * @throws IOException           when the path cannot be inspected
     * @throws IllegalStateException when this lease is closed
     */
    @NonNull
    Describe read() throws IOException;

    /**
     * Returns the file's content in a form, as it stands under this lease.
     *
     * @param form form to read the content in
     * @return content in the form
     * @throws Form.Unreadable when the content is not in the form
     * @throws IOException     as {@link #read()} throws
     */
    @NonNull
    default String read(@NonNull Form form) throws IOException {
      return form.decode(read());
    }
  }

  /**
   * A lease that changes its file. Plates grant no other lease on the path
   * until it closes.
   */
  non-sealed interface Write extends Lease {

    /**
     * Replaces the whole file with content verified against the evidence it
     * declares. The path never holds partial content. A file that does not
     * exist yet has no node to lease, and is created through its directory
     * instead.
     *
     * @param content bytes to write
     * @throws Violation.Forbidden   when the plate's model does not make
     *                               the file
     *                               {@link Rule.Permission#EDITABLE editable}
     * @throws Refusal               with {@link Refusal.Occupied} when
     *                               something other than a file is at the path
     *                               or a file lies above it, or
     *                               {@link Refusal.Denied} when the file may
     *                               not be written
     * @throws IOException           when writing or verifying fails
     * @throws IllegalStateException when this lease is closed
     */
    void replace(@NonNull Describe content) throws IOException;

    /**
     * Replaces the whole file with an edit of its current content, verified
     * against the evidence the edit declares. The current content stays
     * readable until the new content is in place.
     *
     * @param edit derives the new content from the current content
     * @throws Violation.Forbidden   when the plate's model does not make
     *                               the file
     *                               {@link Rule.Permission#EDITABLE editable}
     * @throws Refusal               with {@link Refusal.Missing} when nothing
     *                               is at the path, {@link Refusal.Occupied}
     *                               when something other than a file is there
     *                               or a file lies above it, or
     *                               {@link Refusal.Denied} when the file may
     *                               not be written
     * @throws IOException           when reading, editing, writing or
     *                               verifying fails
     * @throws IllegalStateException when this lease is closed
     */
    void update(@NonNull Edit edit) throws IOException;

    /**
     * Replaces the whole file with an edit of its current content in a form,
     * written back in that form, as {@link #update(Edit)} does with bytes.
     *
     * @param form form to read and write the content in
     * @param edit derives the new content from the current content
     * @throws Form.Unreadable when the current content is not in the form
     * @throws IOException     as {@link #update(Edit)} throws
     */
    default void update(@NonNull Form form, @NonNull UnaryOperator<String> edit) throws IOException {
      update(before -> form.encode(before, edit.apply(form.decode(before))));
    }

    /**
     * Deletes the file. Directories it leaves empty stay. The file then leaves
     * the tree, though the layout may still declare its place.
     *
     * @throws Violation.Forbidden   when the plate's model does not make
     *                               the file
     *                               {@link Rule.Permission#DELETABLE deletable}
     * @throws Refusal               as {@link #update(Edit)} refuses
     * @throws IOException           when deleting fails
     * @throws IllegalStateException when this lease is closed
     */
    void delete() throws IOException;
  }

  /**
   * A lease that reads and changes its file, for work that checks a file
   * before it writes it.
   */
  interface Mutual extends Read, Write {
  }

  /**
   * A lease that creates a file at a place the tree holds nothing, as
   * {@link Node.Directory#claim(Address)} grants once it has checked the
   * tree. Plates grant no other lease on the path until it closes.
   */
  non-sealed interface Create extends Lease {

    /**
     * Creates the file and any directories above it that do not exist,
     * writing content verified against the evidence it declares. The path
     * never holds partial content. The tree then holds the file and each
     * directory made above it.
     *
     * @param content bytes to write
     * @return the created file
     * @throws Refusal               with {@link Refusal.Occupied} when
     *                               something has come to be at the path or a
     *                               file has come to lie above it since the
     *                               lease was granted, or
     *                               {@link Refusal.Denied} when the path may
     *                               not be written
     * @throws IOException           when writing or verifying fails
     * @throws IllegalStateException when this lease is closed, or has already
     *                               created its file
     */
    @NonNull
    Node.File create(@NonNull Describe content) throws IOException;
  }

  /**
   * Derives a file's new content from its current content.
   */
  @FunctionalInterface
  interface Edit {

    /**
     * Derives the new content.
     *
     * @param before current content, readable while the lease is open
     * @return new content
     * @throws IOException when the current content cannot be read
     */
    @NonNull
    Describe edit(@NonNull Describe before) throws IOException;
  }

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
  sealed interface Form permits Form.Text, Form.Json {

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

    /**
     * Content as text in a charset, read and written strictly: bytes the
     * charset does not decode are unreadable, and text it cannot encode is
     * refused, rather than either being replaced. Text has no empty document,
     * since an empty file is just one more text.
     *
     * @param charset charset the file's text is encoded in
     */
    record Text(@NonNull Charset charset) implements Form {

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

    /**
     * Content as JSON, read leniently, allowing the comments and trailing
     * commas game files carry, and written in one fixed indented layout.
     *
     * <p>
     * Its content is the array or object at a place in a document, as text
     * with one line per element or per property: each written as compact JSON
     * with its object keys sorted, a property as an object holding only that
     * property, and nothing else, no brackets and no commas between them.
     * Writing puts them back in place, keeping the rest of the document, so
     * the file stays well formed and no line is ever tied to its neighbours
     * by punctuation. One element or property always reads as the same line,
     * however the file lays it out or orders its keys. A blank file, or a
     * document that does not reach the place yet, reads as empty; writing
     * makes the place, as an array, along with any objects on the way.
     *
     * <p>
     * Whole documents are also {@link #readTree(Describe) read into Jackson's
     * tree} or {@link #readValue(Describe, Class) bound to values} of any type,
     * and {@link #writeValue(String, Object) written back from them}, wherever
     * the place is; the {@link #mapper() mapper} does anything else Jackson
     * can.
     *
     * @param at where in a document the content a configuration edits is
     */
    record Json(@NonNull JsonPointer at) implements Form {

      private static final JsonMapper MAPPER = JsonMapper.builder()
          .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS, JsonReadFeature.ALLOW_TRAILING_COMMA)
          .build();

      private static final JsonMapper LINE = JsonMapper.builder()
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .build();

      /**
       * Describes JSON whose lines are at a place in its documents.
       */
      public Json {
      }

      /**
       * Describes JSON whose lines are its documents' root, such as the array
       * a World's pack list is.
       */
      public Json() {
        this(JsonPointer.empty());
      }

      @Override
      public String decode(@NonNull Describe content) throws IOException {
        if (content.text().isBlank()) {
          return "";
        }
        JsonNode place = readTree(content).at(at);
        if (place.isMissingNode()) {
          return "";
        }
        if (!place.isContainer()) {
          throw new Unreadable(content.path().canonical() + " holds no array or object at " + at + ".", null);
        }
        Stream<JsonNode> members = place.isArray()
            ? place.values().stream()
            : place.properties().stream()
                .map(property -> MAPPER.createObjectNode().set(property.getKey(), property.getValue()));
        return members
            .map(member -> LINE.writeValueAsString(MAPPER.treeToValue(member, Object.class)) + "\n")
            .collect(Collectors.joining());
      }

      @Override
      public Describe encode(@NonNull Describe current, @NonNull String content) throws IOException {
        JsonNode document = current.text().isBlank() ? MissingNode.getInstance() : readTree(current);
        boolean object = document.at(at).isObject();
        ArrayNode elements = MAPPER.createArrayNode();
        ObjectNode properties = MAPPER.createObjectNode();
        for (String line : content.lines().filter(line -> !line.isBlank()).toList()) {
          JsonNode member;
          try {
            member = MAPPER.readTree(line);
          } catch (JacksonException exception) {
            throw new IllegalArgumentException("Each line of JSON content is one JSON value.", exception);
          }
          if (!object) {
            elements.add(member);
            continue;
          }
          if (!member.isObject() || member.size() != 1) {
            throw new Unreadable(current.path().canonical() + " holds an object at " + at
                + ", where each line of content is one property.", null);
          }
          Map.Entry<String, JsonNode> property = member.properties().iterator().next();
          if (properties.has(property.getKey())) {
            throw new IllegalArgumentException("Each property of an object is on one line.");
          }
          properties.set(property.getKey(), property.getValue());
        }
        JsonNode written = object ? properties : elements;
        if (at.matches()) {
          return writeValue(current.fileName(), written);
        }
        // Puts the content in place, making the objects on the way the document does not hold yet
        JsonNode root = document.isContainer() ? document : MAPPER.createObjectNode();
        JsonNode parent = root;
        for (JsonPointer step = at.head(); !step.matches(); step = step.tail()) {
          JsonNode child = parent.isArray()
              ? parent.get(step.getMatchingIndex())
              : parent.get(step.getMatchingProperty());
          if (child == null && parent instanceof ObjectNode holder) {
            child = holder.putObject(step.getMatchingProperty());
          }
          if (child == null || !child.isContainer()) {
            throw new IllegalArgumentException("A document reaches its content through objects and arrays.");
          }
          parent = child;
        }
        JsonPointer last = at.last();
        switch (parent) {
          case ObjectNode holder -> holder.set(last.getMatchingProperty(), written);
          case ArrayNode holder when last.mayMatchElement() && last.getMatchingIndex() < holder.size() ->
              holder.set(last.getMatchingIndex(), written);
          default -> throw new IllegalArgumentException("A document reaches its content through objects and arrays.");
        }
        return writeValue(current.fileName(), root);
      }

      @Override
      public Optional<Describe> empty(@NonNull String fileName) {
        return Optional.of(Describe.of(fileName, at.matches() ? "[]\n" : "{}\n"));
      }

      /**
       * Reads a whole document into Jackson's tree.
       *
       * @param content bytes to read
       * @return the document's root
       * @throws Unreadable  when the bytes are not JSON
       * @throws IOException when the bytes cannot be read
       */
      @NonNull
      public JsonNode readTree(@NonNull Describe content) throws IOException {
        String text = content.text();
        try {
          return MAPPER.readTree(text);
        } catch (JacksonException exception) {
          throw unreadable(content, exception);
        }
      }

      /**
       * Reads a whole document into a value of a type, as Jackson binds it.
       *
       * @param <Value> type read
       * @param content bytes to read
       * @param type    class of the value
       * @return the value the document binds to
       * @throws Unreadable  when the bytes are not JSON, or do not bind to the
       *                     type
       * @throws IOException when the bytes cannot be read
       */
      @NonNull
      public <Value> Value readValue(@NonNull Describe content, @NonNull Class<Value> type) throws IOException {
        String text = content.text();
        try {
          return MAPPER.readValue(text, type);
        } catch (JacksonException exception) {
          throw unreadable(content, exception);
        }
      }

      /**
       * Reads a whole document into a value of a generic type, such as a list
       * of records, as Jackson binds it.
       *
       * @param <Value> type read
       * @param content bytes to read
       * @param type    reference to the value's full type
       * @return the value the document binds to
       * @throws Unreadable  when the bytes are not JSON, or do not bind to the
       *                     type
       * @throws IOException when the bytes cannot be read
       */
      @NonNull
      public <Value> Value readValue(@NonNull Describe content, @NonNull TypeReference<Value> type)
          throws IOException {
        String text = content.text();
        try {
          return MAPPER.readValue(text, type);
        } catch (JacksonException exception) {
          throw unreadable(content, exception);
        }
      }

      /**
       * Writes a value of any type Jackson binds, or a tree, as a whole
       * document known by a file name.
       *
       * @param fileName single filename the bytes are known by
       * @param value    value to write
       * @return reusable description of the document
       * @throws IllegalArgumentException when the filename is not a single
       *                                  filename, or Jackson cannot write
       *                                  the value
       */
      @NonNull
      public Describe writeValue(@NonNull String fileName, @NonNull Object value) {
        try {
          return Describe.of(fileName, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n");
        } catch (JacksonException exception) {
          throw new IllegalArgumentException("A document is written from a value Jackson binds.", exception);
        }
      }

      /**
       * Returns the Jackson mapper this form reads and writes JSON with, for
       * whatever the form does not offer itself.
       *
       * @return immutable, thread-safe mapper
       */
      @NonNull
      public ObjectMapper mapper() {
        return MAPPER;
      }

      /**
       * Describes content that is not readable JSON.
       */
      private Unreadable unreadable(Describe content, JacksonException exception) {
        return new Unreadable(content.path().canonical() + " is not readable JSON.", exception);
      }
    }

    /**
     * Content that is not in the form it was read in, such as text that is not
     * a JSON document, or a document not shaped as its reader requires.
     */
    final class Unreadable extends IOException {

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
  }
}
