package cat.tabbie.sdk.meta.registry;

import java.lang.invoke.MethodHandles;
import java.util.List;

import lombok.NonNull;

/**
 * What one module adds to tabbie: the types it registers, and every type
 * declaring a contribution. A module's processor generates one, and the
 * module provides it as a service; core finds every module's with
 * {@link java.util.ServiceLoader} and reads them into a
 * {@link cat.tabbie.sdk.Registry}, which learns what each type is from its
 * annotations. A module never implements it by hand.
 */
public interface Registration {

  /**
   * Returns the version of the SDK the module was compiled against, so core
   * can refuse a module built for another.
   *
   * @return SDK version, such as {@code 0.1.0}
   */
  @NonNull
  String sdk();

  /**
   * Returns the types the module registers, and every type declaring a
   * contribution: each enum of distributions, abstract type of them
   * registered as a group, content family, and type with a method carrying
   * an action.
   *
   * @return types, in a fixed order
   */
  @NonNull
  List<Class<?>> types();

  /**
   * Returns a lookup reaching the public members of the module's types,
   * including those in packages it does not export, so the registry can
   * call its contributions. It reaches no member that is not public.
   *
   * @return lookup limited to the module's public members
   */
  @NonNull
  MethodHandles.Lookup lookup();
}
