package cat.tabbie.sdk.meta.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import cat.tabbie.sdk.meta.action.Cardinality;
import cat.tabbie.sdk.meta.action.Contributor;
import cat.tabbie.sdk.meta.attribute.Conditions;
import lombok.NonNull;

/**
 * Every contributor to one action the registry read, and the precedence that
 * chooses between them. Any module may contribute to any action, so several
 * contributors may apply to a target, and the action's {@link Cardinality}
 * says how many act. Among those sharing a key, the winners are chosen:
 * <ol>
 * <li>The narrowest wins: one applying to no target, and no type of subject,
 * another doesn't, beats the other. A contributor for a single fork so
 * narrows one for every distribution, and one for a subtype narrows one for
 * its supertype.</li>
 * <li>Among contributors as wide as each other, the one from a module reading
 * the others' modules wins. A module contributing for a type reads the type's
 * module, so a contributor written to replace the type's own just works.</li>
 * <li>Contributors neither rule decides between clash, unless the user prefers
 * exactly one of them.</li>
 * </ol>
 *
 * <p>
 * Two contributors acting on related types, sharing a key, and applying to some
 * target together meet, and their precedence is undetermined until it is
 * decided. Each meeting settles one of two ways: precedence decides it, and
 * when the two come from different modules, the decision is a
 * {@link Replacement}; or precedence decides it neither way, and it joins a
 * {@link Contribution.Clash} with every other undecided meeting it shares a
 * contributor with. Core shows the replacements, so a user sees which module
 * changes what, and shows each clash to the user, who prefers one of its
 * contributors.
 *
 * @param <Call>        the action's signature
 * @param cardinality   how many of the action's contributors act for one
 *                      subject at one target
 * @param contributions every contributor to the action, with its module
 * @param distributions every registered distribution, each by its names, as
 *                      narrowness and meeting compare distributions
 */
public record Contributions<Call>(
    @NonNull Cardinality cardinality,
    @NonNull List<Contributed<Call>> contributions,
    @NonNull Set<Set<String>> distributions) {

  /**
   * Copies the contributors and the distributions.
   */
  public Contributions {
    contributions = List.copyOf(contributions);
    distributions = Set.copyOf(distributions);
  }

  /**
   * Chooses what acts for a subject at a target, with no preference.
   *
   * @param subject the type of what the action acts on
   * @param target  the target it acts for
   * @return the contributors that concur, or the first group that clashes
   */
  public Contribution<Call> choose(@NonNull Class<?> subject, @NonNull Conditions.Target target) {
    return choose(subject, target, Set.of());
  }

  /**
   * Chooses what acts for a subject at a target: among the contributors
   * applying and sharing a key, the narrowest, then the one from a module
   * reading the others' modules, then the one the user prefers. For an action
   * of cardinality {@link Cardinality#EACH EACH}, each key has a winner of its
   * own; for any other, every contributor shares one key.
   *
   * @param subject   the type of what the action acts on
   * @param target    the target it acts for
   * @param preferred {@link Contributed#identity() identities} of the
   *                  contributors the user prefers, as core keeps them
   * @return the contributors that concur, in the order of their keys, none
   *         when none applies; or the first group, in that order, that
   *         precedence and the preferences decide between none of
   */
  public Contribution<Call> choose(
      @NonNull Class<?> subject,
      @NonNull Conditions.Target target,
      @NonNull Set<String> preferred) {
    Map<String, List<Contributed<Call>>> keyed = new LinkedHashMap<>();
    contributions.stream()
        .filter(contributed -> contributed.contributor().subject().isAssignableFrom(subject))
        .filter(contributed -> contributed.contributor().conditions().appliesTo(target))
        .sorted(Comparator.comparing(this::key))
        .forEach(contributed -> keyed.computeIfAbsent(key(contributed), _ -> new ArrayList<>()).add(contributed));
    List<Contributed<Call>> winners = new ArrayList<>();
    for (List<Contributed<Call>> applying : keyed.values()) {
      List<Contributed<Call>> chosen = winning(applying);
      if (chosen.size() > 1) {
        List<Contributed<Call>> preferring = chosen.stream()
            .filter(contributed -> preferred.contains(contributed.identity()))
            .toList();
        if (preferring.size() != 1) {
          return new Contribution.Clash<>(Set.copyOf(chosen));
        }
        chosen = preferring;
      }
      winners.addAll(chosen);
    }
    return new Contribution.Concur<>(winners);
  }

  /**
   * Lists where one module's contributor is chosen over another module's:
   * every meeting between two modules that precedence decides.
   *
   * @return replacements, empty when no two modules' contributors meet
   */
  public List<Replacement> replacements() {
    return undetermined()
        .filter(undetermined -> undetermined.first().module() != undetermined.second().module())
        .flatMap(undetermined -> decide(undetermined).stream())
        .toList();
  }

  /**
   * Lists every clash: the contributors joined by meetings precedence decides
   * neither way, each group once. Preferences settle none of them here; core
   * applies its own when it shows them.
   *
   * @return clashes, empty when precedence decides every meeting
   */
  public List<Contribution.Clash<Call>> clashes() {
    List<Set<Contributed<Call>>> groups = new ArrayList<>();
    undetermined()
        .filter(undetermined -> decide(undetermined).isEmpty())
        .forEach(undetermined -> {
          // An undecided meeting joins every group sharing one of its contributors
          Set<Contributed<Call>> joined = new LinkedHashSet<>(List.of(undetermined.first(), undetermined.second()));
          for (Iterator<Set<Contributed<Call>>> group = groups.iterator(); group.hasNext();) {
            Set<Contributed<Call>> members = group.next();
            if (members.contains(undetermined.first()) || members.contains(undetermined.second())) {
              joined.addAll(members);
              group.remove();
            }
          }
          groups.add(joined);
        });
    return groups.stream()
        .map(Contribution.Clash::new)
        .toList();
  }

  /**
   * Chooses among contributors sharing a key: the narrowest, then the one from
   * a module reading the others' modules.
   */
  private List<Contributed<Call>> winning(List<Contributed<Call>> applying) {
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
   * Returns the key a contributor is chosen by: its own for an action of
   * cardinality {@link Cardinality#EACH EACH}, and one shared by every
   * contributor otherwise.
   */
  private String key(Contributed<Call> contributed) {
    return cardinality == Cardinality.EACH ? contributed.contributor().key().orElse("") : "";
  }

  /**
   * Lists every two contributors that meet: they act on related types, share a
   * key, and their conditions share a target.
   */
  private Stream<Undetermined<Call>> undetermined() {
    return IntStream.range(0, contributions.size())
        .boxed()
        .flatMap(first -> contributions.subList(first + 1, contributions.size()).stream()
            .map(second -> new Undetermined<>(contributions.get(first), second)))
        .filter(undetermined -> {
          Class<?> first = undetermined.first().contributor().subject();
          Class<?> second = undetermined.second().contributor().subject();
          return (first.isAssignableFrom(second) || second.isAssignableFrom(first))
              && key(undetermined.first()).equals(key(undetermined.second()))
              && undetermined.first().contributor().conditions()
                  .overlaps(undetermined.second().contributor().conditions(), distributions);
        });
  }

  /**
   * Decides a meeting as choosing does.
   */
  private Optional<Replacement> decide(Undetermined<Call> undetermined) {
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
   * Whether one contributor is narrower than another: it lies within the
   * other, and the other does not lie within it.
   */
  private boolean narrower(Contributed<?> first, Contributed<?> second) {
    return within(first, second) && !within(second, first);
  }

  /**
   * Whether one contributor lies within another: it acts on the other's type
   * or a type of it, and applies to no target the other doesn't.
   */
  private boolean within(Contributed<?> first, Contributed<?> second) {
    return second.contributor().subject().isAssignableFrom(first.contributor().subject())
        && first.contributor().conditions().within(second.contributor().conditions(), distributions);
  }

  /**
   * Whether one contributor is downstream of another: its module reads the
   * other's, and not the other way around.
   */
  private static boolean downstream(Contributed<?> first, Contributed<?> second) {
    return first.module().canRead(second.module()) && !second.module().canRead(first.module());
  }

  /**
   * A contributor the registry read, and the module declaring it, which
   * decides it over another as wide when it reads the other's module.
   *
   * @param <Call>      the action's signature
   * @param module      module declaring the contributor's method
   * @param contributor the contributor
   */
  public record Contributed<Call>(@NonNull Module module, @NonNull Contributor<Class<?>, Call> contributor) {

    /**
     * Checks every part is given.
     */
    public Contributed {
    }

    /**
     * Names where the contributor comes from, as core shows it.
     *
     * @return the module's name, or {@code the class path} for a module on
     *         it
     */
    public String origin() {
      return module.isNamed() ? module.getName() : "the class path";
    }

    /**
     * Names the contributor stably, as core keeps the user's preferences: where
     * it comes from, and the method as written.
     *
     * @return such as
     *         {@code cat.tabbie.extensions/Mod.installRoot(Install.Context)}
     */
    public String identity() {
      return String.format("%s/%s", origin(), contributor.name());
    }

    /**
     * Names the contributor and where it comes from, as core shows it.
     *
     * @return such as {@code Mod.installRoot(Install.Context), from
     *         cat.tabbie.extensions}
     */
    public String describe() {
      return String.format("%s, from %s", contributor.name(), origin());
    }
  }

  /**
   * One module's contributor chosen over another module's, where both apply: a
   * meeting precedence decides.
   *
   * @param chosen   the contributor chosen where both apply
   * @param replaced the contributor it is chosen over there
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
     * Why one contributor is chosen over another.
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
   * Two contributors that meet, before precedence decides between them: each
   * settles into a replacement, or joins a clash.
   */
  private record Undetermined<Call>(Contributed<Call> first, Contributed<Call> second) {
  }
}
