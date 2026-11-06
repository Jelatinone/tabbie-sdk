package cat.tabbie.sdk.meta.attribute;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import cat.tabbie.sdk.target.Environment;
import cat.tabbie.sdk.target.Platform;
import cat.tabbie.sdk.target.Version;
import lombok.NonNull;

/**
 * Everything a contribution's {@link Applies} declarations say about the
 * targets it applies to: the regions it applies to, read from its own
 * declarations within those of each type enclosing it. It applies to a
 * target when one of its regions does.
 *
 * <p>
 * Two contributions' conditions compare cell by cell. A cell is one
 * registered distribution, in one environment, on one platform, holding the
 * versions the conditions' regions apply to there. Groups so expand into the
 * distributions they hold, and two contributions compare exactly, whichever
 * regions each is written in.
 *
 * @param regions the regions it applies to, at least one
 */
public record Conditions(@NonNull Set<Region> regions) implements Condition {

  /**
   * Every target, as a contribution with no declaration applies to.
   */
  public static final Conditions EVERY = new Conditions(Set.of(Region.EVERY));

  /**
   * Every platform, as cells span them.
   */
  private static final List<Platform> PLATFORMS = Stream.of(Platform.Kernel.values())
      .flatMap(kernel -> Stream.of(Platform.Architecture.values())
          .map(architecture -> new Platform(kernel, architecture)))
      .toList();

  /**
   * Copies the regions, and checks there is one.
   *
   * @throws IllegalArgumentException when there are none
   */
  public Conditions {
    regions = Set.copyOf(regions);
    if (regions.isEmpty()) {
      throw new IllegalArgumentException("A contribution applies to at least one region.");
    }
  }

  /**
   * Reads a contribution's declarations, as the processor and the registry
   * each find them: each level's within the regions of the level enclosing
   * it, so each of its regions takes what it leaves out from each enclosing
   * region. A level declaring none keeps the enclosing regions as they are.
   *
   * @param declarations the declarations at each level, from the outermost
   *                     type enclosing the contribution to the contribution
   *                     itself, each level's as repeated
   * @return what they say together
   * @throws IllegalArgumentException when a declaration is malformed
   */
  public static Conditions of(@NonNull List<List<Applies>> declarations) {
    List<Region> regions = List.of(Region.EVERY);
    for (List<Applies> level : declarations) {
      if (!level.isEmpty()) {
        regions = regions.stream()
            .flatMap(enclosing -> level.stream().map(written -> Region.of(written, enclosing)))
            .toList();
      }
    }
    return new Conditions(Set.copyOf(regions));
  }

  @Override
  public boolean appliesTo(@NonNull Target target) {
    return regions.stream().anyMatch(region -> region.appliesTo(target));
  }

  /**
   * Whether every target these apply to, others apply to as well, so these
   * are no wider: whether in each cell, every version these apply to, the
   * other's apply to.
   *
   * @param other         conditions to compare with
   * @param distributions every registered distribution, each by its names
   * @return whether these lie within the other's in every cell
   */
  public boolean within(@NonNull Conditions other, @NonNull Collection<Set<String>> distributions) {
    return cells(distributions).allMatch(cell -> versions(cell).stream()
        .allMatch(range -> range.within(other.versions(cell))));
  }

  /**
   * Whether these and other conditions share a target: whether in some cell,
   * a version these apply to, the other's apply to.
   *
   * @param other         conditions to compare with
   * @param distributions every registered distribution, each by its names
   * @return whether some target lies within both
   */
  public boolean overlaps(@NonNull Conditions other, @NonNull Collection<Set<String>> distributions) {
    return cells(distributions).anyMatch(cell -> versions(cell).stream()
        .anyMatch(range -> other.versions(cell).stream().anyMatch(range::overlaps)));
  }

  /**
   * Lists every cell: each distribution in each environment on each platform.
   */
  private static Stream<Cell> cells(Collection<Set<String>> distributions) {
    return distributions.stream()
        .flatMap(names -> Stream.of(Environment.values())
            .flatMap(environment -> PLATFORMS.stream()
                .map(platform -> new Cell(names, environment, platform))));
  }

  /**
   * Returns the versions of each region applying in a cell.
   */
  private List<Region.Range> versions(Cell cell) {
    return regions.stream()
        .filter(region -> region.appliesTo(cell.names())
            && region.appliesTo(cell.environment())
            && region.appliesTo(cell.platform()))
        .map(Region::versions)
        .toList();
  }

  /**
   * One target a contribution may be chosen for: a distribution, by every
   * name a declaration may give it, in one environment, on one platform, at
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

  /**
   * One distribution, by its names, in one environment, on one platform.
   */
  private record Cell(Set<String> names, Environment environment, Platform platform) {
  }
}
