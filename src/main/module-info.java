/**
 * Tabbie's software development kit: the contracts that Tabbie's core, its
 * local executor, its command line, and third-party providers share. The SDK
 * describes catalogs, content, targets, and changes; it never writes to a
 * target installation, launches processes, or inspects a machine. Carrying out
 * its descriptions belongs to the consumer.
 *
 * <p>
 * Every package is exported but {@code meta.processor}, until the packages
 * only core uses are exported to core alone. A module declares what
 * it contributes with the {@code meta} actions and attributes, and puts the SDK
 * on its processor path to have those declarations checked, and its
 * registration generated, while it compiles, as the SDK's own are. The
 * compiler the processors run in is needed only then; the registry reads the
 * same annotations at run time.
 *
 * <p>
 * Lombok is a compile-time dependency only; consumers need no Lombok at
 * compile time or at run time. Jackson is part of the SDK's API: the JSON
 * form reads documents into Jackson's tree and binds them to values, so
 * consumers compile and run against it.
 */
module cat.tabbie.sdk {
  requires static java.compiler;
  requires static lombok;
  requires transitive tools.jackson.databind;

  exports cat.tabbie.sdk;
  exports cat.tabbie.sdk.catalog;
  exports cat.tabbie.sdk.content;
  exports cat.tabbie.sdk.distribution;
  exports cat.tabbie.sdk.form;
  exports cat.tabbie.sdk.meta.action;
  exports cat.tabbie.sdk.meta.attribute;
  exports cat.tabbie.sdk.meta.registry;
  exports cat.tabbie.sdk.model;
  exports cat.tabbie.sdk.plate;
  exports cat.tabbie.sdk.platform;
  exports cat.tabbie.sdk.revision;
  exports cat.tabbie.sdk.target;

  uses cat.tabbie.sdk.meta.registry.Registration;

  provides cat.tabbie.sdk.meta.registry.Registration with cat.tabbie.sdk.Registrations;
}
