package cat.tabbie.sdk.minecraft;

import java.util.Set;

import cat.tabbie.sdk.platform.layout.Layout;
import lombok.NonNull;

/**
 * A World as an install's model holds it: the name a Cat selects it by, and
 * the directories holding its data. Its main directory is the one named after
 * it; a server that keeps the Nether and the End apart, as the Bukkit family
 * does, holds them in further directories beside it.
 *
 * <p>
 * A release's layout never names a Cat's Worlds: it declares the directory
 * that accepts Worlds. Core mounts each World's own layout into an install's
 * model, at the World's main directory, and names the Worlds the install
 * applies to in its context; {@link Content content that belongs to a World}
 * is placed in each through the directory there accepting its type. How a
 * World's data is stored is not the SDK's concern.
 */
public interface World {

  /**
   * Returns the name a Cat selects this World by, such as a server's
   * {@code level-name}, which names its main directory.
   *
   * @return non-blank name
   */
  @NonNull
  String name();

  /**
   * Defines the layout directory containing all information of this singular
   * world
   * 
   * TODO: Needs Documentations
   * 
   * @return
   */
  Layout.Directory namespace();

  /**
   * Content that belongs to a World rather than to a Cat, such as a datapack.
   * It installs into the Worlds its context names, so a release's layout does
   * not accept it itself.
   */
  interface Content {
  }

  /**
   * Immutable World.
   *
   * @param name        non-blank name, naming the main directory
   * @param directories directories of the model holding the World's data
   */
  record Default(@NonNull String name, @NonNull Set<Layout.Directory> directories) implements World {

    /**
     * Copies the directories, then checks the name and the directories.
     *
     * @throws IllegalArgumentException when the name is blank, two directories
     *                                  share an address, or none is named
     *                                  after the World
     */
    public Default {
      directories = Set.copyOf(directories);
      if (name.isBlank()) {
        throw new IllegalArgumentException("A World needs a non-blank name.");
      }
      if (directories.stream().map(Layout::address).distinct().count() != directories.size()) {
        throw new IllegalArgumentException("A World's directories must have distinct addresses.");
      }
      if (directories.stream().noneMatch(directory -> directory.name().equals(name))) {
        throw new IllegalArgumentException("A World's main directory is named after it.");
      }
    }
  }
}
