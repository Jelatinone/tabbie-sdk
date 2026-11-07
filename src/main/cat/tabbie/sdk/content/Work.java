package cat.tabbie.sdk.content;

import cat.tabbie.sdk.model.Layout;
import cat.tabbie.sdk.target.Label;
import cat.tabbie.sdk.target.Platform;
import lombok.NonNull;

/**
 * What every action's context holds: the target it acts for, the platform it
 * runs on, the model it places content against, and what it may do with
 * bytes. Each action's context adds its own parts to a work it holds, and
 * answers the work's parts from it rather than copying them.
 */
public interface Work {

  /**
   * Returns the explicitly selected target.
   *
   * @return selected target
   */
  @NonNull
  Label label();

  /**
   * Returns the platform of the machine the work is for, for selecting native
   * content.
   *
   * @return target platform
   */
  @NonNull
  Platform platform();

  /**
   * Returns the model an action places content against: the layout of the
   * Cat's release, with core's own layer and the Worlds the work applies to
   * mounted in it. Whatever already exists in the Cat is not the model's to
   * say.
   *
   * @return model declared from the Cat's root
   */
  @NonNull
  Layout.Directory model();

  /**
   * Returns what an action may do with bytes: capture them into the stash of
   * the work, and inspect and unpack archives within core's limits.
   *
   * @return capture of the work
   */
  @NonNull
  Capture capture();

  /**
   * The work of an action, as core supplies it.
   *
   * @param label    selected target
   * @param platform target platform
   * @param model    model content is placed against, declared from the root
   * @param capture  capture of the work
   */
  record Default(
      @NonNull Label label,
      @NonNull Platform platform,
      @NonNull Layout.Directory model,
      @NonNull Capture capture) implements Work {

    /**
     * Checks the model is declared from the root.
     *
     * @throws IllegalArgumentException when the model is not declared from
     *                                  the root
     */
    public Default {
      if (!model.address().isRoot()) {
        throw new IllegalArgumentException("A model is declared from the root.");
      }
    }
  }
}
