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

import cat.tabbie.sdk.target.Environment;
import cat.tabbie.sdk.target.Platform;
import cat.tabbie.sdk.target.Version;
import lombok.NonNull;

/**
 * The contributors of one action to one subject, by one key, checked together. A
 * target is one distribution, environment and platform, and for every
 * target, no two contributions apply, and every version between the lowest
 * and highest bound of those that do is covered. The targets checked are
 * each value some contribution names, and for each kind of value, one more
 * standing for every value none names.
 *
 * @param <Subject> how the contributions' subject is held, as read while
 *                  compiling or once loaded
 * @param <Call>    how the contributions' method is held, as read while
 *                  compiling or once loaded
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
      List<Contributor<Subject, Call>> applying = contributions.stream()
          .filter(spot::reaches)
          .sorted(Comparator.comparing(
              (Contributor<Subject, Call> contribution) -> contribution.conditions().versions().low().orElse(null),
              Comparator.nullsFirst(Comparator.naturalOrder())))
          .toList();
      if (applying.isEmpty()) {
        continue;
      }
      // The contribution reaching furthest so far; each next one meets it, leaves a gap after it, or overlaps it
      Contributor<Subject, Call> reach = applying.getFirst();
      for (Contributor<Subject, Call> next : applying.subList(1, applying.size())) {
        Optional<Version.Number> ends = reach.conditions().versions().high();
        Optional<Version.Number> begins = next.conditions().versions().low();
        if (ends.isEmpty() || begins.isEmpty() || begins.get().compareTo(ends.get()) < 0) {
          if (reported.add(List.of(Conflict.Overlap.class, reach, next))) {
            conflicts.add(new Conflict.Overlap<>(action, reach, next, spot.toString()));
          }
        } else if (begins.get().compareTo(ends.get()) > 0 && reported.add(List.of(Conflict.Gap.class, reach, next))) {
          conflicts.add(new Conflict.Gap<>(action, reach, next, spot.toString()));
        }
        Optional<Version.Number> reaches = next.conditions().versions().high();
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
    Set<Optional<String>> distributions = values(contribution -> contribution.conditions().distributions().ids(),
        Comparator.<String>naturalOrder());
    Set<Optional<Environment>> environments = values(
        contribution -> contribution.conditions().environments().environments(),
        Comparator.<Environment>naturalOrder());
    Set<Optional<Platform>> platforms = values(contribution -> contribution.conditions().platforms().platforms(),
        Comparator.comparing(Platform::kernel).thenComparing(Platform::architecture));
    return distributions.stream()
        .flatMap(distribution -> environments.stream()
            .flatMap(environment -> platforms.stream()
                .map(platform -> new Spot(distribution, environment, platform))))
        .toList();
  }

  /**
   * Lists the values of one kind the contributions name, in order, then one
   * standing for every value none names.
   */
  private <Value> Set<Optional<Value>> values(
      Function<Contributor<Subject, Call>, Set<Value>> named,
      Comparator<Value> order) {
    return Stream.concat(
        contributions.stream()
            .flatMap(contribution -> named.apply(contribution).stream())
            .sorted(order)
            .map(Optional::of),
        Stream.of(Optional.<Value>empty()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * One target: a distribution, an environment and a platform, each empty
   * for every value no contribution names.
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
     * Whether a contribution applies to this target.
     */
    boolean reaches(Contributor<?, ?> contribution) {
      return contribution.conditions().distributions().includes(distribution)
          && contribution.conditions().environments().includes(environment)
          && contribution.conditions().platforms().includes(platform);
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
     * Two contributions that both apply to a target.
     *
     * @param <Subject> how the contributions' subject is held
     * @param <Call>    how the contributions' method is held
     * @param action        the action as written
     * @param earlier       contribution whose versions begin first
     * @param later         contribution whose versions begin within the
     *                      earlier's
     * @param target        the target both apply to
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
     * @param action        the action as written
     * @param earlier       contribution whose versions end before the gap
     * @param later         contribution whose versions begin after the gap
     * @param target        the target the gap is in
     */
    record Gap<Subject, Call>(
        @NonNull String action,
        @NonNull Contributor<Subject, Call> earlier,
        @NonNull Contributor<Subject, Call> later,
        @NonNull String target) implements Conflict<Subject, Call> {

      /**
       * Checks every part is given, and that there are versions between the
       * two.
       *
       * @throws IllegalArgumentException when the earlier has no high bound,
       *                                  the later has no low bound, or the
       *                                  later's begins no later than the
       *                                  earlier's ends
       */
      public Gap {
        Optional<Version.Number> ends = earlier.conditions().versions().high();
        Optional<Version.Number> begins = later.conditions().versions().low();
        if (ends.isEmpty() || begins.isEmpty() || begins.get().compareTo(ends.get()) <= 0) {
          throw new IllegalArgumentException("A gap lies between where one contribution's versions end and the "
              + "next's begin.");
        }
      }

      @Override
      public String message() {
        Version.Number ends = earlier.conditions().versions().high().orElseThrow();
        Version.Number begins = later.conditions().versions().low().orElseThrow();
        return String.format("%s contributions leave the versions from %s up to %s uncovered for %s: %s ends at "
            + "%s, but %s begins at %s.", action, ends, begins, target, earlier.name(), ends,
            later.name(), begins);
      }
    }
  }
}
