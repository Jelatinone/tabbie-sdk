/**
 * Tabbie's software development kit: the contracts that Tabbie's core, its
 * local executor, its command line, and third-party providers share. The SDK
 * describes catalogs, content, targets, and changes; it never writes to a
 * target installation, launches processes, or inspects a machine. Carrying out
 * its descriptions belongs to the consumer.
 *
 * <p>
 * Every package is exported. Lombok is a compile-time dependency only;
 * consumers need no Lombok at compile time or at run time. Jackson is part of
 * the SDK's API: the JSON form reads documents into Jackson's tree and binds
 * them to values, so consumers compile and run against it.
 */
module cat.tabbie.sdk {
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
  exports cat.tabbie.sdk.minecraft;
  exports cat.tabbie.sdk.minecraft.distribution;
  exports cat.tabbie.sdk.platform;
  exports cat.tabbie.sdk.platform.layout;
}
