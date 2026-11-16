package cat.tabbie.sdk.tree;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.form.Form;
import lombok.NonNull;

/**
 * Why work failed: what a tree, a plate or the registry would not do. A reason
 * is plain data, and the same reason may travel in either carrier: a
 * {@link Violation} when it is found while work describes its changes, and a
 * {@link Refusal} when it is found while work reads or carries them out. The
 * carrier says when; the reason says why. Callers that decide what to do next
 * switch over the reason, never over exception types.
 *
 * <p>
 * Reasons are grouped by what they are about: a {@link Place place} in a tree,
 * the {@link Placement placement} of content, a {@link Change change} that
 * cannot be described, a shape's {@link Content content}, and the
 * {@link Registry registry} of contributions.
 * Every reason states what went wrong as a sentence, and whether the same work
 * may succeed when it is tried again.
 */
public sealed interface Reason {

  /**
   * Describes the failure as a sentence.
   *
   * @return failure message
   */
  @NonNull
  String message();

  /**
   * Whether the same work may succeed when it is tried again, because what
   * failed it can pass, such as another lease releasing a path.
   *
   * @return true when retrying may succeed
   *
   * @apiNote false unless a reason says otherwise.
   */
  default boolean retryable() {
    return false;
  }

  /**
   * A place in a tree does not hold or allow what work asks of it.
   */
  sealed interface Place extends Reason {

    /**
     * Returns the address the work was at.
     *
     * @return address of the place
     */
    @NonNull
    Address address();

    /**
     * Nothing is at an address that work requires something at: nothing
     * exists there.
     *
     * @param address the first address on the way that holds nothing
     */
    record Missing(@NonNull Address address) implements Place {

      @Override
      public String message() {
        return String.format("Nothing is at %s.", address.shown());
      }
    }

    /**
     * Something is in the way of the work: something where a file is to be
     * created, something other than a file where one is to be read, replaced
     * or deleted, a file above the work's address, or a sibling whose name
     * differs from the work's only by case. What the work needed there follows
     * from the work.
     *
     * @param address address the work was at
     * @param at      address of what is in the way: the work's own, one above
     *                it, or a sibling
     * @param found   what is in the way
     */
    record Occupied(@NonNull Address address, @NonNull Address at, @NonNull State found) implements Place {

      /**
       * Checks that something was found.
       *
       * @throws IllegalArgumentException when nothing was found, which is
       *                                  {@link Missing}
       */
      public Occupied {
        if (found instanceof State.Absent) {
          throw new IllegalArgumentException("An occupied place holds something.");
        }
      }

      /**
       * Describes something in the way at the work's own address.
       *
       * @param address address the work was at
       * @param found   what is in the way there
       */
      public Occupied(@NonNull Address address, @NonNull State found) {
        this(address, address, found);
      }

      @Override
      public String message() {
        return at.equals(address)
            ? String.format("Found %s in the way at %s.", found.phrase(), address.shown())
            : String.format("Found %s at %s, in the way of %s.", found.phrase(), at.shown(), address.shown());
      }
    }

    /**
     * A permission work needs does not hold at a place, by the rules that
     * hold there.
     *
     * @param address    address of the work
     * @param permission permission it needs
     */
    record Denied(@NonNull Address address, @NonNull Rule.Permission permission) implements Place {

      @Override
      public String message() {
        return String.format("%s is not %s.", address.shown(), permission.name().toLowerCase(Locale.ROOT));
      }
    }

    /**
     * The filesystem does not permit a write the tree allows.
     *
     * @param address address the work was at
     * @param at      the read-only file, or the directory that would hold it
     */
    record Unwritable(@NonNull Address address, @NonNull Address at) implements Place {

      @Override
      public String message() {
        return at.equals(address)
            ? String.format("The filesystem does not permit writing %s.", address.shown())
            : String.format("The filesystem does not permit writing %s in %s.", address.shown(), at.shown());
      }
    }

    /**
     * Another lease holds an address, on a plate that does not wait. It may
     * pass once that lease closes.
     *
     * @param address address the work was at
     */
    record Held(@NonNull Address address) implements Place {

      @Override
      public String message() {
        return String.format("Another lease holds %s.", address.shown());
      }

      @Override
      public boolean retryable() {
        return true;
      }
    }

    /**
     * An address does not lie in the directory work requires it in.
     *
     * @param address   address that lies elsewhere
     * @param directory directory it must lie in
     */
    record Outside(@NonNull Address address, @NonNull Address directory) implements Place {

      @Override
      public String message() {
        return String.format("%s does not lie in %s.", address.shown(), directory.shown());
      }
    }

    /**
     * An image found its place in another state than it was described from,
     * such as a file someone else edited since, so it changed nothing.
     *
     * @param address address of the image
     * @param found   the state the place was in
     */
    record Changed(@NonNull Address address, @NonNull State found) implements Place {

      @Override
      public String message() {
        return String.format("%s changed since it was described: it holds %s.", address.shown(), found.phrase());
      }
    }
  }

  /**
   * Content has nowhere, or no single place, to go.
   */
  sealed interface Placement extends Reason {

    /**
     * Returns the type of the content.
     *
     * @return content type
     */
    @NonNull
    Class<?> type();

    /**
     * Content is placed where no rule accepts its type.
     *
     * @param address address of the placement
     * @param type    type of the content
     */
    record Unaccepted(@NonNull Address address, @NonNull Class<?> type) implements Placement {

      @Override
      public String message() {
        return String.format("Nothing at %s accepts %s.", address.shown(), type.getSimpleName());
      }
    }

    /**
     * No place accepts content of a type, so there is nowhere to put it.
     *
     * @param type type of the content
     */
    record Unplaced(@NonNull Class<?> type) implements Placement {

      @Override
      public String message() {
        return String.format("No place accepts %s.", type.getSimpleName());
      }
    }

    /**
     * Several places accept content of a type, where the work placing it
     * required exactly one.
     *
     * @param type      type of the content
     * @param addresses immutable addresses of the accepting directories, in
     *                  declaration order
     */
    record Ambiguous(@NonNull Class<?> type, @NonNull List<Address> addresses) implements Placement {

      /**
       * Copies the addresses.
       */
      public Ambiguous {
        addresses = List.copyOf(addresses);
      }

      @Override
      public String message() {
        return String.format("Several places accept %s: %s.",
            type.getSimpleName(),
            addresses.stream()
                .map(Address::shown)
                .collect(Collectors.joining(", ")));
      }
    }
  }

  /**
   * A change cannot be described as requested, because it would be
   * malformed. An image that exists is never malformed: constructing,
   * composing, reducing and validating images fail with one of these instead
   * of producing one.
   */
  sealed interface Change extends Reason {

    /**
     * The change's before and after states are the same, so it changes
     * nothing. Both sides absent, equal content, and chunks that only hold
     * context are all unchanged.
     *
     * @param address file the change was on
     */
    record Unchanged(@NonNull Address address) implements Change {

      @Override
      public String message() {
        return String.format("A change of %s must change something.", address.canonical());
      }
    }

    /**
     * The change names the root itself rather than a file beneath it.
     *
     * @param address the root's address the change named
     */
    record Root(@NonNull Address address) implements Change {

      @Override
      public String message() {
        return "A changed file must lie beneath its root.";
      }
    }

    /**
     * A later change of a file does not continue from the state an earlier
     * change left it in, so the two cannot be applied one after the other.
     *
     * @param address file the changes were on
     */
    record Discontinuous(@NonNull Address address) implements Change {

      @Override
      public String message() {
        return String.format("Changes of %s must continue from one another.", address.canonical());
      }
    }

    /**
     * Changes of different files were composed; only changes of one file
     * compose.
     *
     * @param first  file of the earlier change
     * @param second file of the later change
     */
    record Unrelated(@NonNull Address first, @NonNull Address second) implements Change {

      @Override
      public String message() {
        return String.format("Only changes of one file compose, not %s and %s.", first.canonical(), second.canonical());
      }
    }

    /**
     * Two images of one set change the same file, so the set cannot be
     * exposed in any order.
     *
     * @param address file both images change
     */
    record Clash(@NonNull Address address) implements Change {

      @Override
      public String message() {
        return String.format("A set may change %s only once.", address.canonical());
      }
    }

    /**
     * One image of a set changes a file that another image's file lies
     * beneath, so the path would have to be both a file and a directory.
     *
     * @param directory changed file that another changed file lies beneath
     * @param file      changed file beneath it
     */
    record Nested(@NonNull Address directory, @NonNull Address file) implements Change {

      @Override
      public String message() {
        return String.format("%s cannot be a file while %s lies beneath it.", directory.canonical(), file.canonical());
      }
    }
  }

  /**
   * A shape's content is not what work reads it as: not in a form, broken, of
   * a version the form does not read, unsafe, too large, or not the bytes it
   * declares. Each names the shape it is about.
   */
  sealed interface Content extends Reason {

    /**
     * Returns the shape whose content failed.
     *
     * @return the shape
     */
    @NonNull
    Shape shape();

    /**
     * The shape is not in a form at all, such as bytes that are not JSON read
     * as JSON.
     *
     * @param shape the shape read
     * @param form  the form it was read as
     */
    record Unreadable(@NonNull Shape shape, @NonNull Class<? extends Form> form) implements Content {

      @Override
      public String message() {
        return String.format("%s cannot be read as %s.", shape.address().shown(), form.getSimpleName());
      }
    }

    /**
     * The shape is in a form, but broken: it lacks or contradicts what the form
     * requires.
     *
     * @param shape  the shape read
     * @param form   the form it was read as
     * @param detail what the form requires, as a sentence
     */
    record Malformed(@NonNull Shape shape, @NonNull Class<? extends Form> form, @NonNull String detail)
        implements Content {

      @Override
      public String message() {
        return String.format("%s is not a valid %s: %s", shape.address().shown(), form.getSimpleName(), detail);
      }
    }

    /**
     * The shape is in a form, but uses a version or feature the work cannot
     * read.
     *
     * @param shape  the shape read
     * @param form   the form it was read as
     * @param detail what is not supported, as a sentence
     */
    record Unsupported(@NonNull Shape shape, @NonNull Class<? extends Form> form, @NonNull String detail)
        implements Content {

      @Override
      public String message() {
        return String.format("%s uses what %s does not support here: %s", shape.address().shown(),
            form.getSimpleName(), detail);
      }
    }

    /**
     * An archive holds a path that is not safe to place, such as one leading
     * outside the archive or a name that is not portable.
     *
     * @param shape the archive
     * @param path  the path as the archive writes it
     */
    record Unsafe(@NonNull Shape shape, @NonNull String path) implements Content {

      @Override
      public String message() {
        return String.format("%s holds an unsafe path, %s.", shape.address().shown(), path);
      }
    }

    /**
     * The shape's content exceeds a limit, such as an archive's entry count.
     *
     * @param shape the shape read
     * @param limit the limit exceeded, as a sentence
     */
    record Exceeded(@NonNull Shape shape, @NonNull String limit) implements Content {

      @Override
      public String message() {
        return String.format("%s exceeds a limit: %s", shape.address().shown(), limit);
      }
    }

    /**
     * The bytes read from a file do not match what is declared of them: their
     * size, or a digest both know. It may pass when the bytes are read again,
     * such as from another origin.
     *
     * @param shape    the file read
     * @param expected what is declared of its bytes
     * @param found    what the bytes read are
     */
    record Mismatch(@NonNull Shape shape, @NonNull Describe expected, @NonNull Describe found) implements Content {

      @Override
      public String message() {
        return String.format("The bytes read from %s do not match what is declared of them.",
            shape.address().shown());
      }

      @Override
      public boolean retryable() {
        return true;
      }
    }
  }

  /**
   * What modules register, or what core chooses between, cannot be read or
   * settled.
   */
  sealed interface Registry extends Reason {

    /**
     * Something is registered twice: a type, or a group's name.
     *
     * @param name the type's name, or the group's
     */
    record Duplicate(@NonNull String name) implements Registry {

      @Override
      public String message() {
        return String.format("%s is registered twice.", name);
      }
    }

    /**
     * Something is named that no registrable kind or registration covers, such
     * as a registered type of no registrable kind.
     *
     * @param name what is named
     */
    record Unregistered(@NonNull String name) implements Registry {

      @Override
      public String message() {
        return String.format("No registrable kind or registration covers %s.", name);
      }
    }

    /**
     * A contribution cannot be reached through its module's registration: its
     * method cannot be called, or its action's elements cannot be read.
     *
     * @param name the method or element that cannot be reached
     */
    record Unreachable(@NonNull String name) implements Registry {

      @Override
      public String message() {
        return String.format("%s cannot be reached through its module's registration.", name);
      }
    }

    /**
     * Contributions clash where something must decide between them, and no
     * preference settles it.
     *
     * @param contributors immutable identities of the clashing contributors
     */
    record Clash(@NonNull Set<String> contributors) implements Registry {

      /**
       * Copies the identities, and checks there are at least two.
       *
       * @throws IllegalArgumentException when there are fewer than two
       */
      public Clash {
        contributors = Set.copyOf(contributors);
        if (contributors.size() < 2) {
          throw new IllegalArgumentException("A clash is between at least two contributors.");
        }
      }

      @Override
      public String message() {
        return String.format("%s clash, and no preference chooses between them.", contributors.stream()
            .sorted()
            .collect(Collectors.joining(", ")));
      }
    }
  }
}
