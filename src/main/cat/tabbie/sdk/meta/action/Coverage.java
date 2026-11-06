package cat.tabbie.sdk.meta.action;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cat.tabbie.sdk.meta.attribute.Region;
import cat.tabbie.sdk.target.Environment;
import cat.tabbie.sdk.target.Platform;
import cat.tabbie.sdk.target.Version;
import lombok.NonNull;

/**
 * The contributors of one action to one subject, by one key, checked
 * together. A target is one distribution, environment and platform, and for
 * every target, no two contributors apply to one version, and every version
 * between the lowest and highest bound of those that do is covered. The
 * targets checked are each value some region names, and for each kind of
 * value, one more standing for every value none names. A group of
 * distributions is a value of its own here, since which distributions it
 * holds is known only once every module is loaded; the registry compares
 * contributors through the distributions each group holds.
 *
 * <p>
 * Each contributor's own regions applying to a target are joined first, so a
 * contributor never conflicts with itself, and the versions between two of
 * its regions are simply not supported.
 *
 * @param <Subject>     how the contributions' subject is held, as read while
 *                      compiling or once loaded
 * @param <Call>        how the contributions' method is held, as read while
 *                      compiling or once loaded
 * @param action        the action as written, such as {@code @Install}
 * @param contributions its contributions, in the order they are declared
 */
public record Coverage<Subject, Call>(
    @NonNull String action,
    @NonNull List<Contributor<Subject, Call>> contributions) {

  /**
   * Copies the contributions.
   */
  public Coverage {
    contributions = List.copyOf(contributions);
  }

  /**
   * Finds every conflict between the contributions, each pair once, at the
   * first target it is found at.
   *
   * @return conflicts, empty when the contributions agree
   */
  public List<Conflict<Subject, Call>> conflicts() {
    List<Conflict<Subject, Call>> conflicts = new ArrayList<>();
    Set<List<Object>> reported = new HashSet<>();
    for (Spot spot : spots()) {
      List<Piece<Subject, Call>> pieces = contributions.stream()
          .flatMap(contribution -> Region.Range.union(contribution.conditions().regions().stream()
              .filter(spot::reaches)
              .map(Region::versions)
              .toList()).stream()
              .map(range -> new Piece<>(contribution, range)))
          .sorted(Comparator.comparing(Piece::range, Region.Range.LOW_ORDER))
          .toList();
      if (pieces.isEmpty()) {
        continue;
      }
      // The piece reaching furthest so far; each next one meets it, leaves a gap after it, or overlaps it
      Piece<Subject, Call> reach = pieces.getFirst();
      for (Piece<Subject, Call> next : pieces.subList(1, pieces.size())) {
        Optional<Version.Number> ends = reach.range().high();
        Optional<Version.Number> begins = next.range().low();
        boolean another = !reach.contribution().equals(next.contribution());
        if (ends.isEmpty() || begins.isEmpty() || begins.get().compareTo(ends.get()) < 0) {
          if (another && reported.add(List.of(Conflict.Overlap.class, reach.contribution(), next.contribution()))) {
            conflicts.add(new Conflict.Overlap<>(action, reach.contribution(), next.contribution(), spot.toString()));
          }
        } else if (begins.get().compareTo(ends.get()) > 0 && another
            && reported.add(List.of(Conflict.Gap.class, reach.contribution(), next.contribution()))) {
          conflicts.add(new Conflict.Gap<>(action, reach.contribution(), next.contribution(), spot.toString(),
              ends.get(), begins.get()));
        }
        Optional<Version.Number> reaches = next.range().high();
        if (ends.isPresent() && (reaches.isEmpty() || reaches.get().compareTo(ends.get()) > 0)) {
          reach = next;
        }
      }
    }
    return List.copyOf(conflicts);
  }

  /**
   * Lists every target to check, in a fixed order.
   */
  private List<Spot> spots() {
    Set<Optional<String>> distributions = values(Region::distributions, Comparator.<String>naturalOrder());
    Set<Optional<Environment>> environments = values(Region::environments, Comparator.<Environment>naturalOrder());
    Set<Optional<Platform>> platforms = values(Region::platforms,
        Comparator.comparing(Platform::kernel).thenComparing(Platform::architecture));
    return distributions.stream()
        .flatMap(distribution -> environments.stream()
            .flatMap(environment -> platforms.stream()
                .map(platform -> new Spot(distribution, environment, platform))))
        .toList();
  }

  /**
   * Lists the values of one kind the contributions' regions name, in order,
   * then one standing for every value none names.
   */
  private <Value> Set<Optional<Value>> values(Function<Region, Set<Value>> named, Comparator<Value> order) {
    return Stream.concat(
        contributions.stream()
            .flatMap(contribution -> contribution.conditions().regions().stream())
            .flatMap(region -> named.apply(region).stream())
            .sorted(order)
            .map(Optional::of),
        Stream.of(Optional.<Value>empty()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * One target: a distribution, an environment and a platform, each empty
   * for every value no region names.
   *
   * @param distribution distribution identifier
   * @param environment  environment
   * @param platform     platform
   */
  private record Spot(
      Optional<String> distribution,
      Optional<Environment> environment,
      Optional<Platform> platform) {

    /**
     * Whether a region applies to this target.
     */
    boolean reaches(Region region) {
      return (region.distributions().isEmpty() || distribution.filter(region.distributions()::contains).isPresent())
          && (region.environments().isEmpty() || environment.filter(region.environments()::contains).isPresent())
          && (region.platforms().isEmpty() || platform.filter(region.platforms()::contains).isPresent());
    }

    /**
     * Names the target by the values it has.
     */
    @Override
    public String toString() {
      String named = Stream.of(distribution, environment.map(Enum::name),
          platform.map(machine -> machine.kernel() + "/" + machine.architecture()))
          .flatMap(Optional::stream)
          .collect(Collectors.joining(", "));
      return named.isEmpty() ? "every target" : named;
    }
  }

  /**
   * One contribution's versions at a target, joined from its regions there.
   *
   * @param contribution the contribution
   * @param range        versions it applies to there, apart from its others
   */
  private record Piece<Subject, Call>(Contributor<Subject, Call> contribution, Region.Range range) {
  }

  /**
   * Two contributions of one action that disagree about a target, reported
   * on the later of the two.
   *
   * @param <Subject> how the contributions' subject is held
   * @param <Call>    how the contributions' method is held
   */
  public sealed interface Conflict<Subject, Call> permits Conflict.Overlap, Conflict.Gap {

    /**
     * Returns the later of the two contributions, where the conflict is
     * reported.
     *
     * @return later contribution
     */
    @NonNull
    Contributor<Subject, Call> later();

    /**
     * Describes the conflict as a complete sentence.
     *
     * @return message
     */
    @NonNull
    String message();

    /**
     * Two contributions that both apply to a version of a target.
     *
     * @param <Subject> how the contributions' subject is held
     * @param <Call>    how the contributions' method is held
     * @param action    the action as written
     * @param earlier   contribution whose versions begin first
     * @param later     contribution whose versions begin within the
     *                  earlier's
     * @param target    the target both apply to
     */
    record Overlap<Subject, Call>(
        @NonNull String action,
        @NonNull Contributor<Subject, Call> earlier,
        @NonNull Contributor<Subject, Call> later,
        @NonNull String target) implements Conflict<Subject, Call> {

      /**
       * Checks every part is given.
       */
      public Overlap {
      }

      @Override
      public String message() {
        return String.format("Two %s contributions, %s and %s, both apply to %s; no two may apply to one target.",
            action, earlier.name(), later.name(), target);
      }
    }

    /**
     * Two contributions to a target with versions between them that neither
     * applies to.
     *
     * @param <Subject> how the contributions' subject is held
     * @param <Call>    how the contributions' method is held
     * @param action    the action as written
     * @param earlier   contribution whose versions end before the gap
     * @param later     contribution whose versions begin after the gap
     * @param target    the target the gap is in
     * @param ends      where the earlier's versions end, the gap's first
     *                  version
     * @param begins    where the later's versions begin, after the gap
     */
    record Gap<Subject, Call>(
        @NonNull String action,
        @NonNull Contributor<Subject, Call> earlier,
        @NonNull Contributor<Subject, Call> later,
        @NonNull String target,
        @NonNull Version.Number ends,
        @NonNull Version.Number begins) implements Conflict<Subject, Call> {

      /**
       * Checks every part is given, and that there are versions between the
       * two.
       *
       * @throws IllegalArgumentException when the later's versions begin no
       *                                  later than the earlier's end
       */
      public Gap {
        if (begins.compareTo(ends) <= 0) {
          throw new IllegalArgumentException("A gap lies between where one contribution's versions end and the "
              + "next's begin.");
        }
      }

      @Override
      public String message() {
        return String.format("%s contributions leave the versions from %s up to %s uncovered for %s: %s ends at "
            + "%s, but %s begins at %s.", action, ends, begins, target, earlier.name(), ends,
            later.name(), begins);
      }
    }
  }
}
