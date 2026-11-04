package {{package}};

/**
 * What this module adds to tabbie, generated from its registered types and
 * the types declaring its contributions. The module provides it as a
 * service, and core finds it with {@link java.util.ServiceLoader}.
 */
public final class Registrations implements cat.tabbie.sdk.meta.registry.Registration {

  /**
   * Creates the registration, as the service loader does.
   */
  public Registrations() {
  }

  @Override
  public String sdk() {
    return "{{version}}";
  }

  @Override
  public java.util.List<Class<?>> types() {
    return java.util.List.of({{types}});
  }

  @Override
  public java.lang.invoke.MethodHandles.Lookup lookup() {
    return java.lang.invoke.MethodHandles.lookup()
        .dropLookupMode(java.lang.invoke.MethodHandles.Lookup.PACKAGE);
  }
}
