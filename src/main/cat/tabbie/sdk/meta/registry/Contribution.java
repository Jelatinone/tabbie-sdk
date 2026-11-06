package cat.tabbie.sdk.meta.registry;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.NonNull;

/**
 * What the registry answers for an action, a subject and a target: the
 * contributors that act, which concur, or those precedence cannot decide
 * between, which clash. Core acts on a concurrence and shows a clash to the
 * user, whose pick joins the preferences it chooses with from then on.
 *
 * @param <Call> the action's signature
 */
public sealed interface Contribution<Call> permits Contribution.Concur, Contribution.Clash {

  /**
   * Contributors precedence chose: for an action of cardinality
   * {@link cat.tabbie.sdk.meta.action.Cardinality#EACH EACH}, one for each key,
   * and for any other, at most one.
   *
   * @param <Call>       the action's signature
   * @param contributors the contributors that act, in the order of their keys;
   *                     empty when none applies
   */
  record Concur<Call>(@NonNull List<Contributions.Contributed<Call>> contributors) implements Contribution<Call> {

    /**
     * Copies the contributors.
     */
    public Concur {
      contributors = List.copyOf(contributors);
    }
  }

  /**
   * Contributors precedence decides between none of: none is narrower than
   * another, nor from a module reading only another's, and the user prefers
   * none of them, or several.
   *
   * @param <Call>       the action's signature
   * @param contributors the contributors clashing, at least two
   */
  record Clash<Call>(@NonNull Set<Contributions.Contributed<Call>> contributors) implements Contribution<Call> {

    /**
     * Copies the contributors, and checks there are at least two.
     *
     * @throws IllegalArgumentException when there are fewer than two
     */
    public Clash {
      contributors = Set.copyOf(contributors);
      if (contributors.size() < 2) {
        throw new IllegalArgumentException("A clash is between at least two contributors.");
      }
    }

    /**
     * Describes the clash as a complete sentence, as core shows it.
     *
     * @return message
     */
    public String message() {
      return String.format("These clash where they meet, none narrower than another nor from a module reading only "
          + "another's: %s.", contributors.stream()
              .map(Contributions.Contributed::describe)
              .sorted(Comparator.naturalOrder())
              .collect(Collectors.joining("; ")));
    }
  }
}
