/**
 * Tabbie's software development kit: the contracts that Tabbie's core, its
 * local executor, its command line, and third-party providers share. The SDK
 * describes catalogs, content, targets, and changes; it never writes to a
 * target installation, launches processes, or inspects a machine. Carrying out
 * its descriptions belongs to the consumer.
 *
 * <p>
 * The packages divide the SDK by concern:
 * <ul>
 * <li>{@link cat.tabbie.sdk.meta.action}, {@link cat.tabbie.sdk.meta.attribute}
 * and {@link cat.tabbie.sdk.meta.registry}: the annotations a module declares
 * what it contributes with, its registration, and the registry reading every
 * module's into the distributions, content families and contributions core
 * chooses between</li>
 * <li>{@link cat.tabbie.sdk.content}: content, and how it is installed</li>
 * <li>{@link cat.tabbie.sdk.distribution}: runtime families, and the files
 * their releases carry</li>
 * <li>{@link cat.tabbie.sdk.target}: game versions, and the targets content
 * is installed on</li>
 * <li>{@link cat.tabbie.sdk.tree}: shapes, their leases and rules,
 * addresses and namespaces</li>
 * <li>{@link cat.tabbie.sdk.failure}: why work fails, and the violations and
 * refusals carrying the reason</li>
 * <li>{@link cat.tabbie.sdk.revision}: descriptions of change, the deferred
 * work that carries them out, and the plates it runs on</li>
 * <li>{@link cat.tabbie.sdk.form}: shapes read in a format</li>
 * <li>{@link cat.tabbie.sdk.catalog}: providers, their projects, releases and
 * coordinates</li>
 * <li>{@link cat.tabbie.sdk.platform}: machines, packages, toolchains,
 * commands, and the observer</li>
 * </ul>
 *
 * <p>
 * Every package is exported to every module but {@code meta.processor}, which
 * runs only in the compiler. A module declares what it contributes with the
 * {@code meta} actions and attributes, and puts the SDK on its processor path
 * to have those declarations checked, and its registration index written,
 * while it compiles, as the SDK's own are. It exports every package holding a
 * contribution, and declares nothing else for core to find it. The compiler
 * the processors run in is needed only then; the registry reads the same
 * annotations at run time.
 *
 * <p>
 * Lombok is a compile-time dependency only; consumers need no Lombok at
 * compile time or at run time. The SDK depends on nothing else: formats such
 * as JSON live in the modules that read them.
 */
module cat.tabbie.sdk {
  requires static java.compiler;
  requires static lombok;

  exports cat.tabbie.sdk.catalog;
  exports cat.tabbie.sdk.content;
  exports cat.tabbie.sdk.distribution;
  exports cat.tabbie.sdk.failure;
  exports cat.tabbie.sdk.form;
  exports cat.tabbie.sdk.meta.action;
  exports cat.tabbie.sdk.meta.attribute;
  exports cat.tabbie.sdk.meta.registry;
  exports cat.tabbie.sdk.platform;
  exports cat.tabbie.sdk.revision;
  exports cat.tabbie.sdk.target;
  exports cat.tabbie.sdk.tree;
}
