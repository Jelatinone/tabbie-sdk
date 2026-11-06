package cat.tabbie.sdk.meta.attribute;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

import cat.tabbie.sdk.target.Environment;

/**
 * The targets a contribution applies to: one region of distributions,
 * versions, environments and platforms, which a {@link Region} reads.
 * Repeated, it names several regions, and the contribution applies to each,
 * so one method can apply to several distributions, each at versions of its
 * own, such as {@code @Applies(to = "java:launcher", low = "1.14")} beside
 * {@code @Applies(to = "java:forge", low = "1.13")}.
 *
 * <p>
 * Placed on a type, it applies to every contribution within it, in nested
 * types too. Declarations nest element by element: what a nearer declaration
 * leaves out, it takes from the declaration enclosing it, so a family's
 * interface can say once which distributions its contributions serve, and
 * each method only the versions its own logic holds for. Each region of a
 * nearer declaration takes what it leaves out from each region enclosing it.
 * Outside every declaration, each element is every value. An empty array or
 * text leaves an element out, and {@link #ANY} names every value explicitly,
 * since an annotation cannot tell an element left out from one written as its
 * default.
 *
 * <p>
 * Bounds read in the edition of the distributions a region applies to, so a
 * region with bounds applies to distributions of one edition, and one naming
 * distributions of another edition than the region enclosing it gives bounds
 * of its own.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(Applies.List.class)
public @interface Applies {

  /**
   * Every value, written explicitly: as a bound, no bound; as the only
   * distribution, every distribution.
   */
  String ANY = "*";

  /**
   * A namespaced distribution identifier: {@code bedrock:} or {@code java:}
   * followed by lowercase letters and digits, optionally in hyphen-separated
   * words. The text before the colon is the edition.
   */
  Pattern IDENTIFIER = Pattern.compile("(bedrock|java):[a-z0-9]+(-[a-z0-9]+)*");

  /**
   * Returns the distributions the region applies to, each by identifier, such
   * as {@code bedrock:vanilla}, or by the identifier of a group of them, such
   * as {@code java:launcher} for every Java mod loader. Identifiers are text so
   * that any module can name any distribution, including one another module
   * contributes.
   *
   * @return namespaced distribution identifiers, {@link #ANY} alone for every
   *         distribution, or empty to take the enclosing region's
   */
  String[] to() default {};

  /**
   * Returns the first version the region applies to, a
   * {@link cat.tabbie.sdk.target.Version.Number release number} written as an
   * identifier writes it, such as {@code 1.20.5} or {@code 26.3}.
   *
   * @return release number, {@link #ANY} for no lower bound, or empty to take
   *         the enclosing region's
   */
  String low() default "";

  /**
   * Returns the first version the region no longer applies to, so two regions
   * where one's high bound is the other's low bound meet without sharing a
   * version.
   *
   * @return release number above the low bound, {@link #ANY} for no upper
   *         bound, or empty to take the enclosing region's
   */
  String high() default "";

  /**
   * Returns the environments the region applies to, such as only dedicated
   * servers.
   *
   * @return environments, or empty to take the enclosing region's
   */
  Environment[] environments() default {};

  /**
   * Returns the platforms the region applies to, such as a native server
   * binary built for Linux on x64.
   *
   * @return platforms, or empty to take the enclosing region's
   */
  Platform[] platforms() default {};

  /**
   * One platform a region applies to: an operating system kernel together
   * with a processor architecture.
   */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target({})
  @interface Platform {

    /**
     * Returns the platform's operating system kernel.
     *
     * @return kernel
     */
    cat.tabbie.sdk.target.Platform.Kernel kernel();

    /**
     * Returns the platform's processor architecture.
     *
     * @return architecture
     */
    cat.tabbie.sdk.target.Platform.Architecture architecture();
  }

  /**
   * Holds the regions of a type or method naming several; written by the
   * compiler, not by hand.
   */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  @interface List {

    /**
     * Returns the regions, in the order they are written.
     *
     * @return regions
     */
    Applies[] value();
  }
}
