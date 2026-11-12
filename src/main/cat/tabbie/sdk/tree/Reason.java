package cat.tabbie.sdk.tree;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import cat.tabbie.sdk.model.Rule;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

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
 * cannot be described, and the {@link Registry registry} of contributions.
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
   * Shows an address in a message, naming the root itself.
   */
  private static String shown(Address address) {
    return address.isRoot() ? "the root" : address.canonical();
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
     * What a place holds.
     */
    @Getter
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    enum Kind {

      /**
       * Nothing.
       */
      ABSENT("nothing"),

      /**
       * A regular file.
       */
      FILE("a file"),

      /**
       * A directory.
       */
      DIRECTORY("a directory"),

      /**
       * Something other than a file or directory, such as a dangling link.
       */
      OTHER("something other than a file or directory");

      /**
       * How a message names what a place holds, such as "a directory".
       *
       * @return the phrase
       */
      String phrase;
    }

    /**
     * Nothing is at an address that work requires something at: nothing
     * exists there, or, in a layout, nothing is declared there.
     *
     * @param address the first address on the way that holds nothing
     */
    record Missing(@NonNull Address address) implements Place {

      /**
       * Checks every component is given.
       */
      public Missing {
      }

      @Override
      public String message() {
        return "Nothing is at %s.".formatted(shown(address));
      }
    }

    /**
     * Something other than work requires is in the way: something where a
     * file is to be created, something other than a file where one is to be
     * read, replaced or deleted, or something other than a directory above
     * the work's address.
     *
     * @param address  address the work was at
     * @param at       address that holds it: the work's own, or one above it
     * @param required what the work requires at that address
     * @param found    what that address holds
     */
    record Occupied(
        @NonNull Address address,
        @NonNull Address at,
        @NonNull Kind required,
        @NonNull Kind found) implements Place {

      /**
       * Checks that something other than what is required was found.
       *
       * @throws IllegalArgumentException when nothing was found, which is
       *                                  {@link Missing}, or what was required
       */
      public Occupied {
        if (found == Kind.ABSENT || found == required) {
          throw new IllegalArgumentException("An occupied place holds something other than is required.");
        }
      }

      /**
       * Describes the work's own address holding the wrong kind of thing.
       *
       * @param address  address the work was at
       * @param required what the work requires there
       * @param found    what the address holds
       */
      public Occupied(@NonNull Address address, @NonNull Kind required, @NonNull Kind found) {
        this(address, address, required, found);
      }

      @Override
      public String message() {
        return "Expected %s at %s, found %s.".formatted(required.getPhrase(), shown(at), found.getPhrase());
      }
    }

    /**
     * An address leads beneath a file, where nothing can lie.
     *
     * @param address address of the file
     * @param name    the name beneath it
     */
    record Beneath(@NonNull Address address, @NonNull String name) implements Place {

      /**
       * Checks every component is given.
       */
      public Beneath {
      }

      @Override
      public String message() {
        return "Nothing can lie beneath the file %s, such as %s.".formatted(shown(address), name);
      }
    }

    /**
     * A permission work needs does not hold at a place, by what the layout
     * declares there.
     *
     * @param address    address of the work
     * @param permission permission it needs
     */
    record Denied(@NonNull Address address, @NonNull Rule.Permission permission) implements Place {

      /**
       * Checks every component is given.
       */
      public Denied {
      }

      @Override
      public String message() {
        return "The layout does not make %s %s.".formatted(shown(address), permission);
      }
    }

    /**
     * The filesystem does not permit a write the tree allows.
     *
     * @param address address the work was at
     * @param at      the read-only file, or the directory that would hold it
     */
    record Unwritable(@NonNull Address address, @NonNull Address at) implements Place {

      /**
       * Checks every component is given.
       */
      public Unwritable {
      }

      @Override
      public String message() {
        return at.equals(address)
            ? "The filesystem does not permit writing %s.".formatted(shown(address))
            : "The filesystem does not permit writing %s in %s.".formatted(shown(address), shown(at));
      }
    }

    /**
     * Another lease holds an address, on a plate that does not wait. It may
     * pass once that lease closes.
     *
     * @param address address the work was at
     */
    record Held(@NonNull Address address) implements Place {

      /**
       * Checks every component is given.
       */
      public Held {
      }

      @Override
      public String message() {
        return "Another lease holds %s.".formatted(shown(address));
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

      /**
       * Checks every component is given.
       */
      public Outside {
      }

      @Override
      public String message() {
        return "%s does not lie in %s.".formatted(shown(address), shown(directory));
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
     * Content is placed where the layout does not accept its type.
     *
     * @param address address of the placement
     * @param type    type of the content
     */
    record Unaccepted(@NonNull Address address, @NonNull Class<?> type) implements Placement {

      /**
       * Checks every component is given.
       */
      public Unaccepted {
      }

      @Override
      public String message() {
        return "The layout does not accept %s at %s.".formatted(type.getSimpleName(), shown(address));
      }
    }

    /**
     * No declared directory accepts content of a type, so there is nowhere to
     * place it.
     *
     * @param type type of the content
     */
    record Unplaced(@NonNull Class<?> type) implements Placement {

      /**
       * Checks every component is given.
       */
      public Unplaced {
      }

      @Override
      public String message() {
        return "The layout declares no directory accepting %s.".formatted(type.getSimpleName());
      }
    }

    /**
     * Several declared directories accept content of a type, where the work
     * placing it required exactly one.
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
        return "The layout declares several directories accepting %s: %s.".formatted(
            type.getSimpleName(),
            addresses.stream()
                .map(Reason::shown)
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

      /**
       * Checks every component is given.
       */
      public Unchanged {
      }

      @Override
      public String message() {
        return "A change of %s must change something.".formatted(address.canonical());
      }
    }

    /**
     * The change names the root itself rather than a file beneath it.
     *
     * @param address the root's address the change named
     */
    record Root(@NonNull Address address) implements Change {

      /**
       * Checks every component is given.
       */
      public Root {
      }

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

      /**
       * Checks every component is given.
       */
      public Discontinuous {
      }

      @Override
      public String message() {
        return "Changes of %s must continue from one another.".formatted(address.canonical());
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

      /**
       * Checks every component is given.
       */
      public Unrelated {
      }

      @Override
      public String message() {
        return "Only changes of one file compose, not %s and %s.".formatted(first.canonical(), second.canonical());
      }
    }

    /**
     * Two images of one set change the same file, so the set cannot be
     * exposed in any order.
     *
     * @param address file both images change
     */
    record Clash(@NonNull Address address) implements Change {

      /**
       * Checks every component is given.
       */
      public Clash {
      }

      @Override
      public String message() {
        return "A set may change %s only once.".formatted(address.canonical());
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

      /**
       * Checks every component is given.
       */
      public Nested {
      }

      @Override
      public String message() {
        return "%s cannot be a file while %s lies beneath it.".formatted(directory.canonical(), file.canonical());
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

      /**
       * Checks every component is given.
       */
      public Duplicate {
      }

      @Override
      public String message() {
        return "%s is registered twice.".formatted(name);
      }
    }

    /**
     * Something is named that no registrable kind or registration covers, such
     * as a registered type of no registrable kind.
     *
     * @param name what is named
     */
    record Unregistered(@NonNull String name) implements Registry {

      /**
       * Checks every component is given.
       */
      public Unregistered {
      }

      @Override
      public String message() {
        return "No registrable kind or registration covers %s.".formatted(name);
      }
    }

    /**
     * A contribution cannot be reached through its module's registration: its
     * method cannot be called, or its action's elements cannot be read.
     *
     * @param name the method or element that cannot be reached
     */
    record Unreachable(@NonNull String name) implements Registry {

      /**
       * Checks every component is given.
       */
      public Unreachable {
      }

      @Override
      public String message() {
        return "%s cannot be reached through its module's registration.".formatted(name);
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
        return "%s clash, and no preference chooses between them.".formatted(contributors.stream()
            .sorted()
            .collect(Collectors.joining(", ")));
      }
    }
  }
}
