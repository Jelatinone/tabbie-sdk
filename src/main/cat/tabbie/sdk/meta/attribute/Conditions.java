package cat.tabbie.sdk.meta.attribute;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import cat.tabbie.sdk.target.Environment;
import cat.tabbie.sdk.target.Platform;
import cat.tabbie.sdk.target.Version;
import lombok.NonNull;

/**
 * Everything a contribution's attributes say about the targets it applies
 * to: its versions, distributions, environments and platforms, each read
 * from the attribute's nearest declaration, and each every target when none
 * is declared. It applies to a target when every one of them does.
 *
 * @param versions      versions it applies to
 * @param distributions distributions it applies to
 * @param environments  environments it applies to
 * @param platforms     platforms it applies to
 */
public record Conditions(
    @NonNull Versions.Meta versions,
    @NonNull Distributions.Meta distributions,
    @NonNull Environments.Meta environments,
    @NonNull Platforms.Meta platforms) implements Condition {

  /**
   * Every target, as a contribution with no attributes applies to.
   */
  public static final Conditions EVERY = new Conditions(Versions.Meta.EVERY, Distributions.Meta.EVERY,
      Environments.Meta.EVERY, Platforms.Meta.EVERY);

  /**
   * Checks every part is given.
   */
  public Conditions {
  }

  /**
   * Reads what the nearest declaration of each attribute says, as the
   * processor and the registry each find them.
   *
   * @param versions      nearest {@link Versions}, or empty when none is
   *                      declared
   * @param distributions nearest {@link Distributions}, or empty when none is
   *                      declared
   * @param environments  nearest {@link Environments}, or empty when none is
   *                      declared
   * @param platforms     nearest {@link Platforms}, each as repeated, empty
   *                      when none is declared
   * @return what they say together
   * @throws IllegalArgumentException when an attribute is malformed
   */
  public static Conditions of(
      @NonNull Optional<Versions> versions,
      @NonNull Optional<Distributions> distributions,
      @NonNull Optional<Environments> environments,
      @NonNull List<Platforms> platforms) {
    return new Conditions(
        versions.map(Versions.Meta::of).orElse(Versions.Meta.EVERY),
        distributions.map(Distributions.Meta::of).orElse(Distributions.Meta.EVERY),
        environments.map(Environments.Meta::of).orElse(Environments.Meta.EVERY),
        Platforms.Meta.of(platforms.toArray(Platforms[]::new)));
  }

  @Override
  public boolean appliesTo(@NonNull Target target) {
    return versions.appliesTo(target)
        && distributions.appliesTo(target)
        && environments.appliesTo(target)
        && platforms.appliesTo(target);
  }

  /**
   * Whether every target these apply to, others apply to as well, so these
   * are no wider. Distributions are compared by what they name, through the
   * groups they name, so naming a distribution is narrower than naming its
   * group.
   *
   * @param other         conditions to compare with
   * @param distributions every registered distribution, each by its names
   * @return whether these lie within the other's in every attribute
   */
  public boolean within(@NonNull Conditions other, @NonNull Collection<Set<String>> distributions) {
    return versions.within(other.versions)
        && this.distributions.within(other.distributions, distributions)
        && environments.within(other.environments)
        && platforms.within(other.platforms);
  }

  /**
   * Whether these and other conditions share a target: whether every one of
   * their attributes shares a value.
   *
   * @param other         conditions to compare with
   * @param distributions every registered distribution, each by its names
   * @return whether some target lies within both
   */
  public boolean overlaps(@NonNull Conditions other, @NonNull Collection<Set<String>> distributions) {
    return versions.overlaps(other.versions)
        && this.distributions.overlaps(other.distributions, distributions)
        && environments.overlaps(other.environments)
        && platforms.overlaps(other.platforms);
  }

  /**
   * One target a contribution may be chosen for: a distribution, by every
   * name an attribute may give it, in one environment, on one platform, at
   * one game version.
   *
   * @param distributions the distribution's identifier, and the identifier of
   *                      every group it belongs to
   * @param environment   the target's environment
   * @param platform      the platform the target runs on
   * @param version       the target's game version
   */
  public record Target(
      @NonNull Set<String> distributions,
      @NonNull Environment environment,
      @NonNull Platform platform,
      @NonNull Version version) {

    /**
     * Copies the distribution's names.
     */
    public Target {
      distributions = Set.copyOf(distributions);
    }
  }
}
