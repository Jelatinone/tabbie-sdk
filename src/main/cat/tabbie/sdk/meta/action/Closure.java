package cat.tabbie.sdk.meta.action;

import java.lang.annotation.Annotation;
import java.util.stream.Stream;

import cat.tabbie.sdk.tree.Namespace;
import lombok.NonNull;

/**
 * Where an action acts: the place the registry has a contribution act in,
 * called once. An action whose contributions may act in different places
 * declares one for each, as its annotation's value, and acts in the root
 * unless it says otherwise; an action that declares none always acts in the
 * root. Each closure says what a release's layout must accept for core to
 * find where a contribution acts, and the
 * {@link cat.tabbie.sdk.Registry#check(cat.tabbie.sdk.catalog.Product.Build)
 * registry checks} every release against the actions that declare one.
 */
public enum Closure {

  /**
   * In the root of the work's model: the Cat itself, at the directory
   * accepting the subject.
   */
  ROOT {
    @Override
    public Class<?> accepted(@NonNull Class<?> subject) {
      return subject;
    }
  },

  /**
   * Beneath the head of every namespace the work applies to, all at once.
   */
  NAMESPACES {
    @Override
    public Class<?> accepted(@NonNull Class<?> subject) {
      return Namespace.class;
    }
  };

  /**
   * Returns what a release's layout accepts in exactly one directory, so core
   * finds where a contribution acting here acts: at the root, the subject
   * itself, whose accepting directory is the contribution's head; in
   * namespaces, {@link Namespace namespaces}, where core mounts each
   * namespace's own layout.
   *
   * @param subject the type the contribution acts on
   * @return the type the layout accepts in exactly one directory
   */
  public abstract Class<?> accepted(@NonNull Class<?> subject);

  /**
   * Whether an action declares where it acts, as its annotation's
   * {@code Closure value()}. Only those actions have a place in a release's
   * layout to act in; the rest act in the root without one.
   *
   * @param action an action's annotation
   * @return whether the action declares a closure
   */
  public static boolean declaredBy(@NonNull Class<? extends Annotation> action) {
    return Stream.of(action.getDeclaredMethods())
        .anyMatch(element -> element.getName().equals("value") && element.getReturnType() == Closure.class);
  }
}
