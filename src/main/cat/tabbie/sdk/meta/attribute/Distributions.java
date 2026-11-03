package cat.tabbie.sdk.meta.attribute;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.NonNull;

/**
 * The distributions an action applies to, by identifier, such as
 * {@code bedrock:vanilla}. Identifiers are text so that any module can name
 * any distribution, including one another module contributes; their form is
 * checked while the module compiles.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Distributions {

  /**
   * Returns the distributions the action applies to.
   *
   * @return at least one namespaced distribution identifier
   */
  String[] value();

  /**
   * What a {@link Distributions} attribute says, read and checked.
   *
   * @param ids distribution identifiers, empty for every distribution
   */
  record Meta(@NonNull Set<String> ids) {

    /**
     * A namespaced distribution identifier: {@code bedrock:} or {@code java:}
     * followed by lowercase letters and digits, optionally in hyphen-separated
     * words.
     */
    public static final Pattern IDENTIFIER = Pattern.compile("(bedrock|java):[a-z0-9]+(-[a-z0-9]+)*");

    /**
     * Every distribution, as an action without the attribute applies to.
     */
    public static final Meta EVERY = new Meta(Set.of());

    /**
     * Copies the identifiers and checks their form.
     *
     * @throws IllegalArgumentException when an identifier is not namespaced
     */
    public Meta {
      ids = Set.copyOf(ids);
      ids.stream()
          .filter(id -> !IDENTIFIER.matcher(id).matches())
          .findFirst()
          .ifPresent(id -> {
            throw new IllegalArgumentException(
                String.format("%s is not a distribution identifier such as bedrock:vanilla.", id));
          });
    }

    /**
     * Reads what an attribute says.
     *
     * @param distributions attribute as written
     * @return its distributions
     * @throws IllegalArgumentException when it names none, or an identifier
     *                                  is not namespaced
     */
    public static Meta of(@NonNull Distributions distributions) {
      if (distributions.value().length == 0) {
        throw new IllegalArgumentException("@Distributions names nothing; leave it out to apply to every "
            + "distribution.");
      }
      return new Meta(Arrays.stream(distributions.value()).collect(Collectors.toSet()));
    }

    /**
     * Whether a distribution is among these.
     *
     * @param id distribution identifier, or empty for one no contribution
     *           names
     * @return whether this applies to every distribution, or names it
     */
    public boolean includes(@NonNull Optional<String> id) {
      return ids.isEmpty() || id.filter(ids::contains).isPresent();
    }
  }
}
