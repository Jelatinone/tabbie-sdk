package cat.tabbie.sdk.tree;

import java.util.Set;

/**
 * A place inside an archive, made by the archive's {@link cat.tabbie.sdk.form.Form
 * form}: a file stored in it, or a directory its paths imply. Entries are
 * walked like any shape, and an entry's bytes are read from the archive only
 * when its lease reads them, checked against what the archive declares of
 * them. An archive is read-only: an entry's write lease is refused.
 */
public sealed interface Entry extends Shape permits Entry.File, Entry.Directory {

  /**
   * Returns what an archive allows: reading alone.
   *
   * @return {@link Rule.Permission#READABLE}
   */
  @Override
  default Set<Rule.Permission> permissions() {
    return Set.of(Rule.Permission.READABLE);
  }

  /**
   * A file stored in an archive, possibly absent.
   */
  non-sealed interface File extends Entry, Shape.File {
  }

  /**
   * A directory inside an archive, possibly absent, such as the archive's own
   * root.
   */
  non-sealed interface Directory extends Entry, Shape.Directory {
  }
}
