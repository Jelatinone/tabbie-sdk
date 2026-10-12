package cat.tabbie.sdk.api;

import lombok.NonNull;

/**
 * A typed read request to a local or remote queryable resource.
 *
 * @param <Criterion> selection criteria interpreted by the provider
 */
public sealed interface Query<Criterion> {

  /**
   * Returns the request's selection criteria.
   *
   * @return non-null selection criteria
   */
  Criterion criteria();

  /**
   * A request for whether any item matches.
   *
   * @param <Criterion> criteria type
   * @param criteria    selection criteria
   */
  record Exists<Criterion>(@NonNull Criterion criteria) implements Query<Criterion> {
  }

  /**
   * A request for the number of matching items.
   *
   * @param <Criterion> criteria type
   * @param criteria    selection criteria
   */
  record Count<Criterion>(@NonNull Criterion criteria) implements Query<Criterion> {
  }

  /**
   * A request for one matching item.
   *
   * @param <Criterion> criteria type
   * @param criteria    selection criteria
   */
  public record Singular<Criterion>(@NonNull Criterion criteria) implements Query<Criterion> {
  }

  /**
   * A collection request for at most {@code limit} matching items, first in the
   * provider's documented order. No limit means every matching item.
   *
   * @param <Criterion> criteria type
   * @param criteria    selection criteria
   * @param limit       positive maximum result count, or null for no bound
   */
  public record Several<Criterion>(@NonNull Criterion criteria, Integer limit)
      implements Query<Criterion> {

    /**
     * Requests every matching item.
     *
     * @param criteria selection criteria
     */
    public Several(Criterion criteria) {
      this(criteria, null);
    }

    /**
     * Checks the optional limit.
     *
     * @throws IllegalArgumentException when the limit is not positive
     */
    public Several {
      if (limit != null && limit < 1) {
        throw new IllegalArgumentException("limit must be positive");
      }
    }
  }
}
