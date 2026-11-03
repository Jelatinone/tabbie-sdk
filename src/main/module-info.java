/**
 * Tabbie's software development kit: the contracts that Tabbie's core, its
 * local executor, its command line, and third-party providers share. The SDK
 * describes catalogs, content, targets, and changes; it never writes to a
 * target installation, launches processes, or inspects a machine. Carrying out
 * its descriptions belongs to the consumer.
 *
 * <p>
 * Every package is exported. A module declares what it contributes with the
 * {@code meta} actions and attributes, and puts the SDK on its processor path
 * to have those declarations checked while it compiles by
 * {@code Action.Processor}, as the SDK's own are. The compiler the processor
 * runs in is needed only then.
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
  exports cat.tabbie.sdk.addon;
  exports cat.tabbie.sdk.addon.artifact;
  exports cat.tabbie.sdk.album.repository;
  exports cat.tabbie.sdk.album.revision;
  exports cat.tabbie.sdk.album.model;
  exports cat.tabbie.sdk.api;
  exports cat.tabbie.sdk.merchant;
  exports cat.tabbie.sdk.meta.action;
  exports cat.tabbie.sdk.meta.attribute;
  exports cat.tabbie.sdk.minecraft;
  exports cat.tabbie.sdk.minecraft.distribution;
  exports cat.tabbie.sdk.platform;
  exports cat.tabbie.sdk.platform.layout;
}
