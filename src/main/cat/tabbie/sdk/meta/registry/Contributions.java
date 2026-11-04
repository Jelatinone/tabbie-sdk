package cat.tabbie.sdk.meta.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import cat.tabbie.sdk.meta.action.Contribution;
import cat.tabbie.sdk.meta.attribute.Conditions;
import lombok.NonNull;

/**
 * Every contribution to one action the registry read, and the precedence
 * that chooses between them. Any module may contribute to any action, so
 * several contributions may apply to a target, and one is chosen:
 * <ol>
 * <li>The narrowest wins: one applying to no target, and no type of subject,
 * another doesn't, beats the other. A contribution for a single fork so
 * narrows one for every distribution, and one for a subtype narrows one for
 * its supertype.</li>
 * <li>Among contributions as wide as each other, the one from a module
 * reading the others' modules wins. A module contributing for a type reads
 * the type's module, so a contribution written to replace the type's own just
 * works.</li>
 * <li>Contributions neither rule decides between {@link #clashes() clash}, and
 * choosing between them fails.</li>
 * </ol>
 *
 * <p>
 * Two contributions acting on related types and applying to some target
 * together meet, and their precedence is undetermined until it is decided.
 * Each meeting settles one of two ways: precedence decides it, and when the
 * two come from different modules, the decision is a {@link Replacement}; or
 * precedence decides it neither way, and it joins a {@link Clash} with every
 * other undecided meeting it shares a contribution with. Core shows the
 * replacements, so a user sees which module changes what, and refuses to start
 * while there is a clash.
 *
 * @param <Call>        the action's signature
 * @param contributions every contribution to the action, with its module
 * @param distributions every registered distribution, each by its names, as
 *                      narrowness and meeting compare distributions
 */
public record Contributions<Call>(
    @NonNull List<Contributed<Call>> contributions,
    @NonNull Set<Set<String>> distributions) {

  /**
   * Copies the contributions and the distributions.
   */
  public Contributions {
    contributions = List.copyOf(contributions);
    distributions = Set.copyOf(distributions);
  }

  /**
   * Chooses the contributions applying to a subject at a target that win by
   * precedence: the narrowest, then among those, the one from a module
   * reading the others' modules.
   *
   * @param subject the type of what the action acts on
   * @param target  the target it acts for
   * @return the one chosen; empty when none applies, and several when
   *         precedence decides between none of them
   */
  public List<Contributed<Call>> choose(@NonNull Class<?> subject, @NonNull Conditions.Target target) {
    List<Contributed<Call>> applying = contributions.stream()
        .filter(contributed -> contributed.contribution().subject().isAssignableFrom(subject))
        .filter(contributed -> contributed.contribution().conditions().appliesTo(target))
        .toList();
    List<Contributed<Call>> narrowest = applying.stream()
        .filter(contributed -> applying.stream().noneMatch(other -> narrower(other, contributed)))
        .toList();
    List<Contributed<Call>> downstream = narrowest.stream()
        .filter(contributed -> narrowest.stream().allMatch(other -> other == contributed
            || downstream(contributed, other)))
        .toList();
    return downstream.isEmpty() ? narrowest : downstream;
  }

  /**
   * Lists where one module's contribution is chosen over another module's:
   * every meeting between two modules that precedence decides.
   *
   * @return replacements, empty when no two modules' contributions meet
   */
  public List<Replacement> replacements() {
    return undetermined()
        .filter(undetermined -> undetermined.first().module() != undetermined.second().module())
        .flatMap(undetermined -> decide(undetermined).stream())
        .toList();
  }

  /**
   * Lists every clash: the contributions joined by meetings precedence
   * decides neither way, each group once.
   *
   * @return clashes, empty when precedence decides every meeting
   */
  public List<Clash> clashes() {
    List<Set<Contributed<?>>> groups = new ArrayList<>();
    undetermined()
        .filter(undetermined -> decide(undetermined).isEmpty())
        .forEach(undetermined -> {
          // An undecided meeting joins every group sharing one of its contributions
          Set<Contributed<?>> joined = new LinkedHashSet<>(List.of(undetermined.first(), undetermined.second()));
          for (Iterator<Set<Contributed<?>>> group = groups.iterator(); group.hasNext();) {
            Set<Contributed<?>> members = group.next();
            if (members.contains(undetermined.first()) || members.contains(undetermined.second())) {
              joined.addAll(members);
              group.remove();
            }
          }
          groups.add(joined);
        });
    return groups.stream()
        .map(Clash::new)
        .toList();
  }

  /**
   * Lists every two contributions that meet: they act on related types, and
   * their conditions share a target.
   */
  private Stream<Undetermined> undetermined() {
    return IntStream.range(0, contributions.size())
        .boxed()
        .flatMap(first -> contributions.subList(first + 1, contributions.size()).stream()
            .map(second -> new Undetermined(contributions.get(first), second)))
        .filter(undetermined -> {
          Class<?> first = undetermined.first().contribution().subject();
          Class<?> second = undetermined.second().contribution().subject();
          return (first.isAssignableFrom(second) || second.isAssignableFrom(first))
              && undetermined.first().contribution().conditions()
                  .overlaps(undetermined.second().contribution().conditions(), distributions);
        });
  }

  /**
   * Decides a meeting as choosing does.
   */
  private Optional<Replacement> decide(Undetermined undetermined) {
    Contributed<?> first = undetermined.first();
    Contributed<?> second = undetermined.second();
    if (narrower(first, second)) {
      return Optional.of(new Replacement(first, second, Replacement.Reason.NARROWER));
    }
    if (narrower(second, first)) {
      return Optional.of(new Replacement(second, first, Replacement.Reason.NARROWER));
    }
    if (downstream(first, second)) {
      return Optional.of(new Replacement(first, second, Replacement.Reason.DOWNSTREAM));
    }
    if (downstream(second, first)) {
      return Optional.of(new Replacement(second, first, Replacement.Reason.DOWNSTREAM));
    }
    return Optional.empty();
  }

  /**
   * Whether one contribution is narrower than another: it lies within the
   * other, and the other does not lie within it.
   */
  private boolean narrower(Contributed<?> first, Contributed<?> second) {
    return within(first, second) && !within(second, first);
  }

  /**
   * Whether one contribution lies within another: it acts on the other's
   * type or a type of it, and applies to no target the other doesn't.
   */
  private boolean within(Contributed<?> first, Contributed<?> second) {
    return second.contribution().subject().isAssignableFrom(first.contribution().subject())
        && first.contribution().conditions().within(second.contribution().conditions(), distributions);
  }

  /**
   * Whether one contribution is downstream of another: its module reads the
   * other's, and not the other way around.
   */
  private static boolean downstream(Contributed<?> first, Contributed<?> second) {
    return first.module().canRead(second.module()) && !second.module().canRead(first.module());
  }

  /**
   * A contribution the registry read, and the module declaring it, which
   * decides it over another as wide when it reads the other's module.
   *
   * @param <Call>       the action's signature
   * @param module       module declaring the contribution's method
   * @param contribution the contribution
   */
  public record Contributed<Call>(@NonNull Module module, @NonNull Contribution<Class<?>, Call> contribution) {

    /**
     * Checks every part is given.
     */
    public Contributed {
    }

    /**
     * Names where the contribution comes from, as core shows it.
     *
     * @return the module's name, or {@code the class path} for a module on
     *         it
     */
    public String origin() {
      return module.isNamed() ? module.getName() : "the class path";
    }

    /**
     * Names the contribution and where it comes from, as core shows it.
     *
     * @return such as {@code Mod.installRoot(Install.Context), from
     *         cat.tabbie.sdk}
     */
    public String describe() {
      return String.format("%s, from %s", contribution.contributor(), origin());
    }
  }

  /**
   * One module's contribution chosen over another module's, where both
   * apply: a meeting precedence decides.
   *
   * @param chosen   the contribution chosen where both apply
   * @param replaced the contribution it is chosen over there
   * @param reason   why it is chosen
   */
  public record Replacement(
      @NonNull Contributed<?> chosen,
      @NonNull Contributed<?> replaced,
      @NonNull Reason reason) {

    /**
     * Checks every part is given.
     */
    public Replacement {
    }

    /**
     * Describes the replacement as a complete sentence, as core shows it.
     *
     * @return message
     */
    public String message() {
      return String.format("%s, is chosen over %s, where both apply: %s.", chosen.describe(), replaced.describe(),
          switch (reason) {
            case NARROWER -> "it is narrower";
            case DOWNSTREAM -> "its module reads the other's";
          });
    }

    /**
     * Why one contribution is chosen over another.
     */
    public enum Reason {

      /**
       * It applies to no target, or type of subject, the other doesn't.
       */
      NARROWER,

      /**
       * It is as wide as the other, and its module reads the other's.
       */
      DOWNSTREAM
    }
  }

  /**
   * Contributions joined by meetings precedence decides neither way: each
   * meets at least one other in the group undecided, though two of them may
   * meet decided or not meet at all.
   *
   * @param contributions the contributions clashing, at least two
   */
  public record Clash(@NonNull Set<Contributed<?>> contributions) {

    /**
     * Copies the contributions, and checks there are at least two.
     *
     * @throws IllegalArgumentException when there are fewer than two
     */
    public Clash {
      contributions = Set.copyOf(contributions);
      if (contributions.size() < 2) {
        throw new IllegalArgumentException("A clash is between at least two contributions.");
      }
    }

    /**
     * Describes the clash as a complete sentence, as core shows it.
     *
     * @return message
     */
    public String message() {
      return String.format("These clash where they meet, none narrower than another nor from a module reading only "
          + "another's: %s.", contributions.stream()
              .map(Contributed::describe)
              .sorted(Comparator.naturalOrder())
              .collect(Collectors.joining("; ")));
    }
  }

  /**
   * Two contributions that meet, before precedence decides between them:
   * each settles into a replacement, or joins a clash.
   */
  private record Undetermined(Contributed<?> first, Contributed<?> second) {
  }
}
