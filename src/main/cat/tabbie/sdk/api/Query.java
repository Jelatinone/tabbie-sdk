package cat.tabbie.sdk.api;

import lombok.NonNull;

/**
 * A typed read request to a local or remote queryable resource.
 *
 * @param <Criterion> selection criteria interpreted by the provider
 */
public sealed interface Query<Criterion> {

  /** @return non-null selection criteria */
  Criterion criteria();

  /**
   * Exist
   *
   * @param <Criterion> criteria type
   * @param criteria    selection criteria
   */
  public record Exists<Criterion>(@NonNull Criterion criteria) implements Query<Criterion> {
  }

  /**
   * Count
   *
   *
   *
   * @param <Criterion> criteria type
   * @param criteria    selection criteria
   */
  public record Count<Criterion>(@NonNull Criterion criteria) implements Query<Criterion> {
  }

  /**
   * Singular
   *
   * A item request
   *
   * @param <Criterion> criteria type
   * @param criteria    selection criteria
   */
  public record Singular<Criterion>(@NonNull Criterion criteria) implements Query<Criterion> {
  }

  /**
   * Several
   *
   * A collection request; no limit means all matching results.
   *
   * @param <Criterion> criteria type
   * @param criteria    selection criteria
   * @param limit       positive maximum result count, or null for no bound
   */
  public record Several<Criterion>(@NonNull Criterion criteria, Integer limit) implements Query<Criterion> {
    /**
     * Requests all matching results.
     *
     * @param criteria selection criteria
     */
    public Several(Criterion criteria) {
      this(criteria, null);
    }

    public Several {
      if (limit != null && limit < 1) {
        throw new IllegalArgumentException("limit must be positive");
      }
    }
  }
}
