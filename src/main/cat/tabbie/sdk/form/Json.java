package cat.tabbie.sdk.form;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * @param at where in a document the content a configuration edits is, as an
 *           RFC 6901 pointer such as {@code /pack}, empty for the root
 */
public record Json(@NonNull String at) implements Form {

  private static final JsonMapper MAPPER = JsonMapper.builder()
      .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS, JsonReadFeature.ALLOW_TRAILING_COMMA)
      .build();

  private static final JsonMapper LINE = JsonMapper.builder()
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
      .build();

  /**
   * Describes JSON whose lines are at a place in its documents, checking
   * the place is a pointer. History records the pointer only as text.
   *
   * @throws IllegalArgumentException when the place is not an RFC 6901
   *                                  pointer
   */
  public Json {
    JsonPointer.compile(at);
  }

  /**
   * Describes JSON whose lines are its documents' root, such as the array
   * a World's pack list is.
   */
  public Json() {
    this("");
  }

  @Override
  public String decode(@NonNull Describe content) throws IOException {
    if (content.text().isBlank()) {
      return "";
    }
    JsonNode place = readTree(content).at(JsonPointer.compile(at));
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
    JsonPointer pointer = JsonPointer.compile(at);
    JsonNode document = current.text().isBlank() ? MissingNode.getInstance() : readTree(current);
    boolean object = document.at(pointer).isObject();
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
    if (pointer.matches()) {
      return writeValue(current.fileName(), written);
    }
    // Puts the content in place, making the objects on the way the document does
    // not hold yet
    JsonNode root = document.isContainer() ? document : MAPPER.createObjectNode();
    JsonNode parent = root;
    for (JsonPointer step = pointer.head(); !step.matches(); step = step.tail()) {
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
    JsonPointer last = pointer.last();
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
    return Optional.of(Describe.of(fileName, at.isEmpty() ? "[]\n" : "{}\n"));
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
