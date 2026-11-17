package cat.tabbie.sdk.revision;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import cat.tabbie.sdk.failure.Reason;
import cat.tabbie.sdk.failure.Refusal;
import cat.tabbie.sdk.failure.Violation;
import cat.tabbie.sdk.tree.Address;
import cat.tabbie.sdk.tree.Describe;
import cat.tabbie.sdk.tree.Lease;
import cat.tabbie.sdk.tree.Node;
import cat.tabbie.sdk.tree.Shape;
import cat.tabbie.sdk.tree.Source;
import cat.tabbie.sdk.tree.State;
import lombok.NonNull;

/**
 * One reversible change to one file beneath a root: its existence, as an
 * {@link Installation installation} from {@link File.Absent absent} to
 * {@link File.Present present} or back, or its content, as a
 * {@link Configuration configuration} by a {@link Revision}. Each image has a
 * side before and a side after, typed by its kind, and its preimage exchanges
 * them. Describing an image performs no I/O beyond what describing its shapes
 * reads. Images are made on nodes, with
 * {@link Node.Directory#receive(Shape) receive},
 * {@link Node.File#configure(Revision) configure} and
 * {@link Node.File#delete() delete}.
 *
 * <p>
 * An image is a {@link Intermediate.Step step}: exposing it on a
 * {@link Plate} carries its change out through the plate's leases, after
 * checking its file still holds its side before, and passes with its side
 * after. It fails with {@link Reason.Place.Changed} when the file holds
 * something else, changing nothing. Bytes are named by
 * {@link Describe descriptions}, and an image reads them through the shapes
 * they are bound to when it is exposed.
 *
 * <p>
 * An image is always well formed: it changes something, and its file lies
 * beneath its root. Describing a change that is not throws a
 * {@link Violation} whose {@link Reason.Change} says why.
 *
 * @param <Side> what each side of the image is
 */
public sealed interface Image<Side extends Image.Alteration> extends Intermediate.Step<Side>
    permits Image.Installation, Image.Configuration {

  /**
   * Returns the changed file.
   *
   * @return file beneath the root
   */
  @NonNull
  Address address();

  /**
   * Returns what the image changes from.
   *
   * @return the side before
   */
  @NonNull
  Side before();

  /**
   * Returns what the image changes to.
   *
   * @return the side after
   */
  @NonNull
  Side after();

  /**
   * Returns the image that undoes this one, exchanging its sides.
   *
   * @return the preimage
   */
  @NonNull
  Image<Side> preimage();

  /**
   * Carries this change out on a plate, when the file still holds this
   * image's side before.
   *
   * @param plate the plate whose root the file lies beneath
   * @return a pass with this image's side after, or a failure with why
   *         nothing changed
   * @throws IOException when the file cannot be inspected or changed, or a
   *                     refusal may pass when tried again
   */
  Scrutiny<Side> expose(@NonNull Plate plate) throws IOException;

  @Override
  default Scrutiny<Side> collapse(@NonNull Plate plate) throws IOException {
    return expose(plate);
  }

  /**
   * Checks that images may be exposed together in any order: each file is
   * changed once, and no changed file lies beneath another.
   *
   * @param images images exposed together
   * @throws Violation with {@link Reason.Change.Clash} when a file is changed
   *                   twice, or {@link Reason.Change.Nested} when a changed
   *                   file lies beneath another
   */
  static void validate(@NonNull Collection<? extends Image<?>> images) {
    Map<Address, Image<?>> files = new LinkedHashMap<>();
    for (Image<?> image : images) {
      if (files.putIfAbsent(image.address(), image) != null) {
        throw new Violation(new Reason.Change.Clash(image.address()));
      }
    }
    for (Address file : files.keySet()) {
      for (Optional<Address> parent = file.parent(); parent.isPresent(); parent = parent.get().parent()) {
        if (files.containsKey(parent.get())) {
          throw new Violation(new Reason.Change.Nested(parent.get(), file));
        }
      }
    }
  }

  /**
   * Returns the preimages of images exposed together.
   *
   * @param images images exposed together
   * @return their preimages
   */
  static Set<Image<?>> preimage(@NonNull Collection<? extends Image<?>> images) {
    return images.stream()
        .map(Image::preimage)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Composes two images of one file, applied one after the other, into one
   * image from the first's side before to the second's side after. Content is
   * read through the descriptions the images name, and combined content is
   * held in memory. Only how the two meet is checked here; each image is
   * checked when it is made.
   *
   * @param first  the earlier image
   * @param second the later image
   * @return the composed image, or empty when the two cancel out
   * @throws Violation   with {@link Reason.Change.Unrelated} when they change
   *                     different files, or {@link Reason.Change.Discontinuous}
   *                     when the second does not continue from the first
   * @throws IOException when content cannot be read
   */
  static Optional<Image<?>> compose(@NonNull Image<?> first, @NonNull Image<?> second) throws IOException {
    Address address = first.address();
    if (!address.equals(second.address())) {
      throw new Violation(new Reason.Change.Unrelated(address, second.address()));
    }
    Supplier<Violation> discontinuous = () -> new Violation(new Reason.Change.Discontinuous(address));
    return switch (first) {
      case Installation earlier -> switch (second) {
        case Installation later -> {
          if (!earlier.after().same(later.before())) {
            throw discontinuous.get();
          }
          yield between(address, earlier.before(), later.after());
        }
        case Configuration<?> later -> {
          if (!(earlier.after() instanceof File.Present(Describe installed))) {
            throw discontinuous.get();
          }
          Shape.File revised = later.revision().revise(installed.shape()).orElseThrow(discontinuous);
          yield between(address, earlier.before(), new File.Present(revised.describe()));
        }
      };
      case Configuration<?> earlier -> switch (second) {
        case Installation later -> {
          if (!(later.before() instanceof File.Present(Describe configured))) {
            throw discontinuous.get();
          }
          Shape.File prior = earlier.revision().invert().revise(configured.shape()).orElseThrow(discontinuous);
          yield between(address, new File.Present(prior.describe()), later.after());
        }
        case Configuration<?> later -> {
          try {
            yield earlier.revision().compose(later.revision())
                .<Image<?>>map(composed -> new Configuration<>(address, composed));
          } catch (IllegalArgumentException exception) {
            throw new Violation(new Reason.Change.Discontinuous(address), exception);
          }
        }
      };
    };
  }

  /**
   * Reduces changes made one after another to the net change from the first
   * one's sides before, composing images of the same file in order.
   *
   * @param changes sets of images, each exposed together, earliest first
   * @return the net images
   * @throws Violation   as {@link #validate(Collection)} and
   *                     {@link #compose(Image, Image)} throw
   * @throws IOException when content cannot be read
   */
  static Set<Image<?>> reduce(@NonNull List<? extends Collection<? extends Image<?>>> changes) throws IOException {
    changes.forEach(Image::validate);
    Map<Address, Image<?>> net = new LinkedHashMap<>();
    Map<Address, File> settled = new HashMap<>();
    for (Collection<? extends Image<?>> change : changes) {
      for (Image<?> image : change) {
        Address address = image.address();
        Image<?> previous = net.remove(address);
        if (previous == null) {
          File file = settled.remove(address);
          if (file != null && image instanceof Installation installation && !file.same(installation.before())) {
            throw new Violation(new Reason.Change.Discontinuous(address));
          }
          net.put(address, image);
          continue;
        }
        Optional<Image<?>> composed = compose(previous, image);
        if (composed.isPresent()) {
          net.put(address, composed.get());
        } else if (previous instanceof Installation installation) {
          settled.put(address, installation.before());
        }
      }
    }
    validate(net.values());
    return Set.copyOf(net.values());
  }

  /**
   * Describes the change between two sides of a file's existence, unless
   * there is none: an installation when one side is absent, and a swap when
   * the file is present on both.
   */
  private static Optional<Image<?>> between(Address address, File before, File after) {
    if (before.same(after)) {
      return Optional.empty();
    }
    if (before instanceof File.Present present && after instanceof File.Present revised) {
      return Optional.of(new Configuration<>(address, new Revision.Swap(present, revised)));
    }
    return Optional.of(new Installation(before, after, address));
  }

  /**
   * One side of an image: what its file is before or after the change.
   */
  sealed interface Alteration permits File, Text {

    /**
     * Whether another side holds the same.
     *
     * @param other the other side
     * @return true when nothing known tells them apart
     */
    boolean same(@NonNull Alteration other);
  }

  /**
   * A file's existence: absent, or present with its content.
   */
  sealed interface File extends Alteration permits File.Absent, File.Present {

    /**
     * Nothing is at the file's place.
     */
    record Absent() implements File {

      @Override
      public boolean same(@NonNull Alteration other) {
        return other instanceof Absent;
      }
    }

    /**
     * The file is present, holding the content its description names.
     *
     * @param content what is known about the file's bytes, bound to where they
     *                are read
     */
    record Present(@NonNull Describe content) implements File {

      @Override
      public boolean same(@NonNull Alteration other) {
        return other instanceof Present present && content.agrees(present.content());
      }
    }
  }

  /**
   * The lines of a text file a diff touches, each region a fragment.
   *
   * @param fragments fragments in file order
   */
  record Text(@NonNull List<Hunk.Fragment> fragments) implements Alteration {

    /**
     * Copies the fragments and checks their order.
     *
     * @throws IllegalArgumentException when the fragments overlap or follow an
     *                                  unterminated line
     */
    public Text {
      fragments = List.copyOf(fragments);
      Hunk.validateFragments(fragments);
    }

    @Override
    public boolean same(@NonNull Alteration other) {
      return equals(other);
    }
  }

  /**
   * A file's existence: created where nothing was, or deleted.
   *
   * @param before  absent when the file is created, or the file deleted
   * @param after   the file created, or absent when it is deleted
   * @param address the file, beneath the root
   */
  record Installation(@NonNull File before, @NonNull File after, @NonNull Address address) implements Image<File> {

    /**
     * Checks the address, and that the image creates or deletes the file.
     *
     * @throws Violation                with {@link Reason.Change.Root} when the
     *                                  address is the root itself, or
     *                                  {@link Reason.Change.Unchanged} when both
     *                                  sides are absent
     * @throws IllegalArgumentException when the file is present on both sides,
     *                                  whose change is a configuration
     */
    public Installation {
      if (address.isRoot()) {
        throw new Violation(new Reason.Change.Root(address));
      }
      if (before instanceof File.Absent && after instanceof File.Absent) {
        throw new Violation(new Reason.Change.Unchanged(address));
      }
      if (before instanceof File.Present && after instanceof File.Present) {
        throw new IllegalArgumentException("A change of a present file's content is a configuration.");
      }
    }

    @Override
    public Installation preimage() {
      return new Installation(after, before, address);
    }

    @Override
    public Scrutiny<File> expose(@NonNull Plate plate) throws IOException {
      Node.File node = plate.root().file(address);
      try (Lease.Write file = node.write()) {
        State held = node.state();
        boolean expected = switch (held) {
          case State.Absent _ -> before instanceof File.Absent;
          case State.File(Describe found) -> before.same(new File.Present(found));
          case State.Directory _,State.Other _ -> false;
        };
        if (!expected) {
          return new Scrutiny.Fail<>(new Reason.Place.Changed(address, held));
        }
        if (after instanceof File.Present(Describe content)) {
          file.replace(content.shape());
        } else {
          file.delete();
        }
        return new Scrutiny.Pass<>(after);
      } catch (Violation violation) {
        return new Scrutiny.Fail<>(violation.reason());
      } catch (Refusal refusal) {
        // A refusal that may pass is tried again instead
        if (refusal.reason().retryable()) {
          throw refusal;
        }
        return new Scrutiny.Fail<>(refusal.reason());
      }
    }
  }

  /**
   * A change to the content of a file that exists, by a revision, whose sides
   * are the image's.
   *
   * @param <Side>   what each side of the revision is
   * @param address  the file, beneath the root
   * @param revision how its content changes
   */
  record Configuration<Side extends Alteration>(@NonNull Address address, @NonNull Revision<Side> revision)
      implements Image<Side> {

    /**
     * Checks the address.
     *
     * @throws Violation with {@link Reason.Change.Root} when the address is the
     *                   root itself
     */
    public Configuration {
      if (address.isRoot()) {
        throw new Violation(new Reason.Change.Root(address));
      }
    }

    @Override
    public Side before() {
      return revision.before();
    }

    @Override
    public Side after() {
      return revision.after();
    }

    @Override
    public Configuration<Side> preimage() {
      return new Configuration<>(address, revision.invert());
    }

    @Override
    public Scrutiny<Side> expose(@NonNull Plate plate) throws IOException {
      Node.File node = plate.root().file(address);
      try (Lease.Write file = node.write()) {
        State held = node.state();
        if (!(held instanceof State.File)) {
          return new Scrutiny.Fail<>(new Reason.Place.Changed(address, held));
        }
        Optional<Shape.File> revised = revision.revise(Source.of(node.name(), file.bytes()));
        if (revised.isEmpty()) {
          return new Scrutiny.Fail<>(new Reason.Place.Changed(address, held));
        }
        file.replace(revised.get());
        return new Scrutiny.Pass<>(revision.after());
      } catch (Violation violation) {
        return new Scrutiny.Fail<>(violation.reason());
      } catch (Refusal refusal) {
        // A refusal that may pass is tried again instead
        if (refusal.reason().retryable()) {
          throw refusal;
        }
        return new Scrutiny.Fail<>(refusal.reason());
      }
    }
  }
}
