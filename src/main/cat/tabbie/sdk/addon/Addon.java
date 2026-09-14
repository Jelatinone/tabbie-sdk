package cat.tabbie.sdk.addon;

import java.time.Instant;
import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

/**
 *
 * <h1>Addon</h1>
 *
 * <p>
 * </p>
 *
 */
public interface Addon {

  /**
   *
   * @return
   */
  @NonNull
  Identity<Provider<?>> providerId();

  /**
   *
   * @return
   */
  @NonNull
  Identity<Addon> addonId();

  /**
   *
   * @return
   */
  @NonNull
  String addonName();

  /**
   *
   * @return
   */
  Set<Builds> builds();

  /**
   *
   * @return
   */
  Set<Tag> tags();

  record Builds(
      @NonNull Identity<Addon> addonId,
      @NonNull Identity<Builds> makeId,

      @NonNull String makeName,
      @NonNull Instant makeDate,
      long makeNumber,

      @NonNull Set<Label> labels) {

    public Builds {
      if (makeNumber < 1L) {
        throw new IllegalArgumentException("revisionNumber must be positive!");
      }
    }
  }

  record Tag(
      @NonNull Identity<Tag> tagId,
      @NonNull String tagName) {
  }

}
