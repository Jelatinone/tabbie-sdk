package cat.tabbie.sdk.meta.attribute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cat.tabbie.sdk.target.Environment;
import cat.tabbie.sdk.target.Platform;
import cat.tabbie.sdk.target.Version;
import lombok.NonNull;

/**
 * What one {@link Applies} declaration says, read within the region
 * enclosing it and checked: the distributions, versions, environments and
 * platforms one region of a contribution applies to. A region's versions read
 * in the edition of its distributions, so a region with bounds applies to
 * distributions of one edition.
 *
 * @param distributions identifiers of distributions and groups of them, empty
 *                      for every distribution
 * @param versions      versions, in the edition of the distributions
 * @param environments  environments, empty for every environment
 * @param platforms     platforms, empty for every platform
 */
public record Region(
    @NonNull Set<String> distributions,
    @NonNull Range versions,
    @NonNull Set<Environment> environments,
    @NonNull Set<Platform> platforms) implements Condition {

  /**
   * Every target, as the region outside every declaration.
   */
  public static final Region EVERY = new Region(Set.of(), Range.EVERY, Set.of(), Set.of());

  /**
   * Copies the values, and checks the identifiers' form and that bounded
   * versions read in one edition.
   *
   * @throws IllegalArgumentException when an identifier is not namespaced, or
   *                                  the versions are bounded while the
   *                                  region applies to every distribution, or
   *                                  to distributions of several editions
   */
  public Region {
    distributions = Set.copyOf(distributions);
    environments = Set.copyOf(environments);
    platforms = Set.copyOf(platforms);
    distributions.stream()
        .filter(id -> !Applies.IDENTIFIER.matcher(id).matches())
        .sorted()
        .findFirst()
        .ifPresent(id -> {
          throw new IllegalArgumentException(
              String.format("%s is not a distribution identifier such as bedrock:vanilla.", id));
        });
    if (!versions.equals(Range.EVERY) && distributions.isEmpty()) {
      throw new IllegalArgumentException(String.format("A region applying to versions %s names its distributions, "
          + "since versions read in the edition of the distributions they apply to.", versions));
    }
    if (!versions.equals(Range.EVERY) && editions(distributions).size() > 1) {
      throw new IllegalArgumentException(String.format("A region applying to versions %s names distributions of "
          + "one edition, rather than %s, since versions read in the edition of the distributions they apply to.",
          versions, String.join(", ", distributions.stream().sorted().toList())));
    }
  }

  /**
   * Reads one declaration within the region enclosing it: each element it
   * leaves out is the enclosing region's, and {@link Applies#ANY} is every
   * value.
   *
   * @param written   declaration as written
   * @param enclosing region enclosing it, {@link #EVERY} for the outermost
   * @return the region it applies to
   * @throws IllegalArgumentException when a bound is not exactly a release
   *                                  number, {@link Applies#ANY} is not alone
   *                                  among distributions, the declaration
   *                                  names distributions of another edition
   *                                  than the bounds it leaves out, or the
   *                                  region is otherwise malformed
   */
  public static Region of(@NonNull Applies written, @NonNull Region enclosing) {
    Set<String> distributions = distributions(written.to(), enclosing.distributions);
    boolean inheritsBounds = written.low().isEmpty() && enclosing.versions.low().isPresent()
        || written.high().isEmpty() && enclosing.versions.high().isPresent();
    if (written.to().length > 0 && inheritsBounds
        && !editions(distributions).equals(editions(enclosing.distributions))) {
      throw new IllegalArgumentException(String.format("A region applying to %s leaves its version bounds out, but "
          + "the enclosing region's, %s, read in another edition; it gives bounds of its own, or Applies.ANY for "
          + "none.", distributions.isEmpty()
              ? "every distribution"
              : String.join(", ", distributions.stream().sorted().toList()),
          enclosing.versions));
    }
    return new Region(distributions,
        new Range(bound(written.low(), enclosing.versions.low()), bound(written.high(), enclosing.versions.high())),
        written.environments().length == 0 ? enclosing.environments : Set.copyOf(List.of(written.environments())),
        written.platforms().length == 0
            ? enclosing.platforms
            : Stream.of(written.platforms())
                .map(platform -> new Platform(platform.kernel(), platform.architecture()))
                .collect(Collectors.toSet()));
  }

  /**
   * Whether this region applies to a target: to its distribution, through one
   * of its names, its environment, its platform and its version.
   *
   * @param target the target a contribution may be chosen for
   * @return whether every element of the region includes the target's
   */
  @Override
  public boolean appliesTo(@NonNull Conditions.Target target) {
    return appliesTo(target.distributions())
        && appliesTo(target.environment())
        && appliesTo(target.platform())
        && versions.appliesTo(target.version());
  }

  /**
   * Whether this region applies to a distribution.
   *
   * @param names the distribution's identifier, and the identifiers of every
   *              group it belongs to
   * @return whether this applies to every distribution, or names one of them
   */
  public boolean appliesTo(@NonNull Set<String> names) {
    return distributions.isEmpty() || names.stream().anyMatch(distributions::contains);
  }

  /**
   * Whether this region applies to an environment.
   *
   * @param environment an environment
   * @return whether this applies to every environment, or names it
   */
  public boolean appliesTo(@NonNull Environment environment) {
    return environments.isEmpty() || environments.contains(environment);
  }

  /**
   * Whether this region applies to a platform.
   *
   * @param platform a platform
   * @return whether this applies to every platform, or names it
   */
  public boolean appliesTo(@NonNull Platform platform) {
    return platforms.isEmpty() || platforms.contains(platform);
  }

  /**
   * Reads the distributions a declaration names within those enclosing it.
   */
  private static Set<String> distributions(String[] written, Set<String> enclosing) {
    List<String> named = List.of(written);
    if (named.isEmpty()) {
      return enclosing;
    }
    if (named.contains(Applies.ANY)) {
      if (named.size() > 1) {
        throw new IllegalArgumentException(String.format("Applies.ANY names every distribution, so it stands alone "
            + "in to(), rather than beside %s.", String.join(", ", named.stream()
                .filter(id -> !id.equals(Applies.ANY))
                .toList())));
      }
      return Set.of();
    }
    return Set.copyOf(named);
  }

  /**
   * Reads a bound as written within the one enclosing it.
   */
  private static Optional<Version.Number> bound(String written, Optional<Version.Number> enclosing) {
    if (written.isEmpty()) {
      return enclosing;
    }
    if (written.equals(Applies.ANY)) {
      return Optional.empty();
    }
    return Optional.of(Version.Number.of(written)
        .filter(number -> number.toString().equals(written))
        .orElseThrow(() -> new IllegalArgumentException(String.format("%s is not a release number such as 1.20.5 "
            + "or 26.3, nor Applies.ANY.", written))));
  }

  /**
   * Returns the editions of some distributions: the text before each
   * identifier's colon.
   */
  private static Set<String> editions(Set<String> distributions) {
    return distributions.stream()
        .map(id -> id.substring(0, Math.max(id.indexOf(':'), 0)))
        .collect(Collectors.toSet());
  }

  /**
   * The versions of one edition from a low bound up to, but not including, a
   * high bound, so two ranges where one's high bound is the other's low bound
   * meet without sharing a version. A bound left out is open.
   *
   * @param low  first version within, or empty for no lower bound
   * @param high first version no longer within, or empty for no upper bound
   */
  public record Range(@NonNull Optional<Version.Number> low, @NonNull Optional<Version.Number> high) {

    /**
     * Every version.
     */
    public static final Range EVERY = new Range(Optional.empty(), Optional.empty());

    /**
     * Orders ranges by their low bound, an open one first.
     */
    public static final Comparator<Range> LOW_ORDER = Comparator.comparing(
        (Range range) -> range.low().orElse(null), Comparator.nullsFirst(Comparator.naturalOrder()));

    /**
     * Checks the low bound is below the high bound.
     *
     * @throws IllegalArgumentException when both are given and the low bound
     *                                  is not below the high bound
     */
    public Range {
      if (low.isPresent() && high.isPresent() && low.get().compareTo(high.get()) >= 0) {
        throw new IllegalArgumentException(String.format(
            "A version range's low bound, %s, is not below its high bound, %s.", low.get(), high.get()));
      }
    }

    /**
     * Joins ranges into as few as hold the same versions: those that overlap
     * or meet end to end become one.
     *
     * @param ranges ranges, in any order
     * @return ranges holding no version in common and not meeting, in order
     */
    public static List<Range> union(@NonNull Collection<Range> ranges) {
      List<Range> joined = new ArrayList<>();
      ranges.stream()
          .sorted(LOW_ORDER)
          .forEach(next -> {
            Optional<Range> last = joined.isEmpty() ? Optional.empty() : Optional.of(joined.getLast());
            if (last.isPresent() && last.get().reaches(next)) {
              joined.removeLast();
              joined.add(new Range(last.get().low, last.get().high.isEmpty() || next.high.isEmpty()
                  ? Optional.empty()
                  : Stream.of(last.get().high.get(), next.high.get()).max(Comparator.naturalOrder())));
            } else {
              joined.add(next);
            }
          });
      return List.copyOf(joined);
    }

    /**
     * Whether a version lies within this range. A version without a release
     * number, such as a week snapshot, lies within no bound, so it lies within
     * only the range of every version.
     *
     * @param version game version of a target
     * @return whether its release number lies within both bounds
     */
    public boolean appliesTo(@NonNull Version version) {
      if (equals(EVERY)) {
        return true;
      }
      return version.number()
          .filter(number -> low.map(bound -> bound.compareTo(number) <= 0).orElse(true))
          .filter(number -> high.map(bound -> number.compareTo(bound) < 0).orElse(true))
          .isPresent();
    }

    /**
     * Whether every version of this range lies within another, so this is no
     * wider.
     *
     * @param other range to compare with
     * @return whether neither bound reaches past the other's
     */
    public boolean within(@NonNull Range other) {
      return other.low.map(bound -> low.filter(own -> own.compareTo(bound) >= 0).isPresent()).orElse(true)
          && other.high.map(bound -> high.filter(own -> own.compareTo(bound) <= 0).isPresent()).orElse(true);
    }

    /**
     * Whether every version of this range lies within some of others
     * together.
     *
     * @param others ranges to compare with, in any order
     * @return whether their union holds every version of this
     */
    public boolean within(@NonNull Collection<Range> others) {
      return union(others).stream().anyMatch(this::within);
    }

    /**
     * Whether this range and another share a version: whether the later of
     * the two low bounds lies below the earlier of the two high bounds.
     *
     * @param other range to compare with
     * @return whether some version lies within both
     */
    public boolean overlaps(@NonNull Range other) {
      Optional<Version.Number> lowest = Stream.of(low, other.low)
          .flatMap(Optional::stream)
          .max(Comparator.naturalOrder());
      Optional<Version.Number> highest = Stream.of(high, other.high)
          .flatMap(Optional::stream)
          .min(Comparator.naturalOrder());
      return lowest.isEmpty() || highest.isEmpty() || lowest.get().compareTo(highest.get()) < 0;
    }

    /**
     * Describes the range as a message names it.
     *
     * @return such as {@code 1.14 up to 1.21}, {@code 1.14 onwards},
     *         {@code below 1.21} or {@code every version}
     */
    @Override
    public String toString() {
      if (low.isPresent() && high.isPresent()) {
        return String.format("%s up to %s", low.get(), high.get());
      }
      return low.map(bound -> bound + " onwards")
          .or(() -> high.map(bound -> "below " + bound))
          .orElse("every version");
    }

    /**
     * Whether a range beginning no earlier than this one overlaps it or meets
     * its end.
     */
    private boolean reaches(Range next) {
      return high.isEmpty() || next.low.isEmpty() || next.low.get().compareTo(high.get()) <= 0;
    }
  }
}
