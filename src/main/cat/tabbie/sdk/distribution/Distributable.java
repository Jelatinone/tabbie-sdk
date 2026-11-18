package cat.tabbie.sdk.distribution;

import java.io.IOException;
import java.util.Set;

import cat.tabbie.sdk.catalog.Identity;
import cat.tabbie.sdk.catalog.Release;
import cat.tabbie.sdk.meta.action.Action;
import cat.tabbie.sdk.revision.Image;
import cat.tabbie.sdk.target.Label;
import cat.tabbie.sdk.target.Platform;
import cat.tabbie.sdk.tree.Shape;
import lombok.NonNull;

/**
 * One file of an exact distribution release, such as a server jar or a loader
 * profile. {@link #install} describes the images that place it, where the
 * {@link cat.tabbie.sdk.meta.action.Place place} for its type says. Installing
 * a distributable captures only its own content: it never downloads runtimes,
 * runs other installers, or starts processes. How a Cat running the release
 * starts, stops and holds its saving is its distribution's
 * {@link cat.tabbie.sdk.meta.action.Start Start},
 * {@link cat.tabbie.sdk.meta.action.Stop Stop} and
 * {@link cat.tabbie.sdk.meta.action.Hold Hold} to say, and carrying any of it
 * out belongs to core.
 *
 * <p>
 * Implementations are records whose compact constructors copy their
 * collections and call {@link #validate(String, Set)}.
 */
public interface Distributable extends Release.Payload {

  /**
   * Stable distributable identity derived from the file coordinates, so equal
   * coordinates always yield the same identity across processes.
   *
   * @return distributable identity
   */
  default Identity<Distributable> distributableId() {
    return Identity.create(coordinates().canonical());
  }

  /**
   * Human-readable canonical distributable name.
   *
   * @return distributable name
   */
  @NonNull
  String distributableName();

  /**
   * Returns this distributable's primary content, as its provider declares
   * it.
   *
   * @return the content's file
   */
  @NonNull
  Shape.File source();

  /**
   * Returns the targets this distributable supports as a whole.
   *
   * @return immutable, nonempty supported targets
   */
  @NonNull
  Set<Label> labels();

  /**
   * Returns the machine platforms this distributable runs on, such as a native
   * server built for one operating system.
   *
   * @return immutable platforms, empty for every platform
   */
  @NonNull
  default Set<Platform> platforms() {
    return Set.of();
  }

  /**
   * Checks whether this distributable supports a target on a platform.
   *
   * @param target   selected target
   * @param platform target machine platform
   * @return whether a label matches the target and the platform is accepted
   */
  default boolean supports(@NonNull Label target, @NonNull Platform platform) {
    return labels().stream().anyMatch(label -> label.match(target))
        && (platforms().isEmpty() || platforms().contains(platform));
  }

  /**
   * Places the source as one file, named after it, at the context's head: the
   * directory the {@link cat.tabbie.sdk.meta.action.Place place} for this
   * distributable's type returns, where a start finds it again. Whoever
   * installs chooses the distributable that {@link #supports} the target while
   * resolving; installing does not ask again.
   *
   * @param context its head, the directory accepting this distributable's
   *                type
   * @return images placing it
   * @throws IOException when the source cannot be described
   */
  @NonNull
  default Set<Image<?>> install(@NonNull Action.Context context) throws IOException {
    return context.head().receive(source());
  }

  /**
   * Checks declarations before a distributable is published.
   *
   * @param distributableName candidate display name
   * @param labels        candidate targets
   * @throws IllegalArgumentException when the name is blank, there are no
   *                                  labels, or labels span distributions
   */
  static void validate(@NonNull String distributableName, @NonNull Set<Label> labels) {
    if (distributableName.isBlank() || labels.isEmpty()) {
      throw new IllegalArgumentException("A distributable needs a non-blank name and targets.");
    }
    if (labels.stream().map(Label::distribution).distinct().count() != 1) {
      throw new IllegalArgumentException("A distributable serves exactly one distribution.");
    }
  }
}
