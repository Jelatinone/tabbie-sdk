package cat.tabbie.sdk.album.revision;

import static cat.tabbie.sdk.TestFixtures.collapse;
import static cat.tabbie.sdk.TestFixtures.scrutinize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import cat.tabbie.sdk.album.revision.Intermediate.Scrutiny;
import cat.tabbie.sdk.api.Observer;

class IntermediateTest {

  @TempDir
  Path scratch;

  @Test
  void construction_runsNothing_untilCollapse_andCollapseRepeatsEffects() throws IOException {
    AtomicInteger runs = new AtomicInteger();

    Intermediate<Integer> work = Intermediate.of(context -> runs.incrementAndGet())
        .map(count -> count * 10)
        .flatMap(count -> Intermediate.of(count + 1))
        .then(Intermediate.of(context -> runs.get()));

    assertEquals(0, runs.get());
    assertEquals(1, collapse(work, scratch));
    assertEquals(2, collapse(work, scratch));
  }

  @Test
  void effects_receiveTheCollapsingContext() throws IOException {
    Intermediate.Step.Context context = new Intermediate.Step.Context.Default(scratch, Observer.none());

    Path seen = Intermediate.of(Intermediate.Step.Context::scratch)
        .flatMap(first -> Intermediate.of(Intermediate.Step.Context::scratch))
        .collapse(context);

    assertSame(scratch, seen);
  }

  @Test
  void then_runsTheNextEffectAfterThisOne() throws IOException {
    List<String> order = new ArrayList<>();

    Intermediate<String> work = Intermediate.of(context -> order.add("first"))
        .then(context -> {
          order.add("second");
          return "done";
        });

    assertEquals("done", collapse(work, scratch));
    assertEquals(List.of("first", "second"), order);
  }

  @Test
  void groupAndAll_collapseInOrder_intoImmutableLists() throws IOException {
    List<String> order = new ArrayList<>();
    List<Intermediate<String>> elements = List.of(
        Intermediate.of(context -> record(order, "a")),
        Intermediate.of(context -> record(order, "b")));

    List<String> grouped = collapse(Intermediate.group(elements), scratch);
    List<String> all = collapse(Intermediate.all(elements), scratch);

    assertEquals(List.of("a", "b"), grouped);
    assertEquals(List.of("a", "b"), all);
    assertEquals(List.of("a", "b", "a", "b"), order);
    assertThrows(UnsupportedOperationException.class, () -> grouped.add("c"));
  }

  @Test
  void all_stopsAtTheFirstFailure() {
    AtomicInteger later = new AtomicInteger();
    List<Intermediate<String>> elements = List.of(
        Intermediate.of(context -> {
          throw new IOException("first fails");
        }),
        Intermediate.of(context -> String.valueOf(later.incrementAndGet())));

    assertThrows(IOException.class, () -> collapse(Intermediate.all(elements), scratch));
    assertEquals(0, later.get());
  }

  @Test
  void any_returnsTheFirstSuccess_withoutTryingLaterAlternatives() throws IOException {
    AtomicInteger later = new AtomicInteger();
    List<Intermediate<String>> alternatives = List.of(
        Intermediate.of(context -> {
          throw new IOException("mirror down");
        }),
        Intermediate.of("mirror"),
        Intermediate.of(context -> String.valueOf(later.incrementAndGet())));

    String result = collapse(Intermediate.any(alternatives), scratch);

    assertEquals("mirror", result);
    assertEquals(0, later.get());
  }

  @Test
  void any_throwsTheFirstFailure_withLaterFailuresSuppressed() {
    IOException first = new IOException("first");
    IOException second = new IOException("second");
    List<Intermediate<String>> alternatives = List.of(
        Intermediate.of(context -> {
          throw first;
        }),
        Intermediate.of(context -> {
          throw second;
        }));

    IOException thrown = assertThrows(IOException.class, () -> collapse(Intermediate.any(alternatives), scratch));

    assertSame(first, thrown);
    assertEquals(List.of(second), List.of(thrown.getSuppressed()));
  }

  @Test
  void any_requiresAnAlternative() {
    assertThrows(IllegalArgumentException.class, () -> Intermediate.any(List.of()));
  }

  @Test
  void any_throwsAFailureSharedByEveryAlternative_unchanged() {
    IOException shared = new IOException("Shared");
    Intermediate<String> failing = Intermediate.of(context -> {
      throw shared;
    });

    IOException thrown = assertThrows(IOException.class,
        () -> collapse(Intermediate.any(List.of(failing, failing)), scratch));

    assertSame(shared, thrown);
    assertEquals(0, thrown.getSuppressed().length);
  }

  @Test
  void group_keepsNullResults() throws IOException {
    List<String> results = collapse(Intermediate.group(List.of(Intermediate.of((String) null), Intermediate.of("a"))),
        scratch);

    assertEquals(Arrays.asList(null, "a"), results);
  }

  @Test
  void step_namesItselfAfterItsEnclosingType() {
    Intermediate.Step<String> step = new Probe();

    assertEquals("IntermediateTest:Probe", step.name());
    assertEquals("Step:Effect", new Intermediate.Step.Effect<>(context -> "x").name());
  }

  @Test
  void stepContext_defaultsToADiscardingObserver() {
    Intermediate.Step.Context context = () -> scratch;

    assertSame(Observer.none(), context.observer());
  }

  @Test
  void value_scrutinizesToAPassOfItsValue() throws IOException {
    Scrutiny<String> scrutiny = scrutinize(Intermediate.of("a"), scratch);

    assertEquals(new Scrutiny.Pass<>("a"), scrutiny);
  }

  @Test
  void transform_mapsAPass_withoutCollapsing() throws IOException {
    Verdict<Integer> source = new Verdict<>(new Scrutiny.Pass<>(2));

    Scrutiny<Integer> scrutiny = scrutinize(source.map(count -> count * 10), scratch);

    assertEquals(new Scrutiny.Pass<>(20), scrutiny);
    assertEquals(1, source.calls().get());
  }

  @Test
  void compress_passesAFailOrMockThrough_withoutCallingItsMapper() throws IOException {
    AtomicInteger mapped = new AtomicInteger();
    Function<String, Intermediate<String>> mapper = value -> {
      mapped.incrementAndGet();
      return Intermediate.of(value);
    };

    Scrutiny<String> failed = scrutinize(new Verdict<>(new Scrutiny.Fail<>("a")).flatMap(mapper), scratch);
    Scrutiny<String> mocked = scrutinize(new Verdict<String>(new Scrutiny.Mock<>()).flatMap(mapper), scratch);

    assertEquals(new Scrutiny.Fail<>(), failed);
    assertEquals(new Scrutiny.Mock<>(), mocked);
    assertEquals(0, mapped.get());
  }

  @Test
  void effectsAndSteps_mock_withoutRunning() throws IOException {
    AtomicInteger runs = new AtomicInteger();

    Scrutiny<Integer> effect = scrutinize(Intermediate.of(context -> runs.incrementAndGet()), scratch);
    Scrutiny<String> step = scrutinize(new Probe(), scratch);

    assertEquals(new Scrutiny.Mock<>(), effect);
    assertEquals(new Scrutiny.Mock<>(), step);
    assertEquals(0, runs.get());
  }

  @Test
  void groupAndAll_scrutinizeToAPass_ofImmutableResultsInOrder() throws IOException {
    List<Intermediate<String>> elements = List.of(
        Intermediate.of((String) null),
        new Verdict<>(new Scrutiny.Pass<>("b")));

    Scrutiny<List<String>> grouped = scrutinize(Intermediate.group(elements), scratch);
    Scrutiny<List<String>> all = scrutinize(Intermediate.all(elements), scratch);

    assertEquals(new Scrutiny.Pass<>(Arrays.asList(null, "b")), grouped);
    assertEquals(new Scrutiny.Pass<>(Arrays.asList(null, "b")), all);
    assertThrows(UnsupportedOperationException.class, () -> grouped.result().add("c"));
  }

  @Test
  void groupAndAll_mock_whenAnyElementMocks() throws IOException {
    Verdict<String> later = new Verdict<>(new Scrutiny.Pass<>("b"));
    List<Intermediate<String>> elements = List.of(new Verdict<>(new Scrutiny.Mock<>()), later);

    Scrutiny<List<String>> grouped = scrutinize(Intermediate.group(elements), scratch);
    Scrutiny<List<String>> all = scrutinize(Intermediate.all(elements), scratch);

    assertEquals(new Scrutiny.Mock<>(), grouped);
    assertEquals(new Scrutiny.Mock<>(), all);
    assertEquals(2, later.calls().get());
  }

  @Test
  void groupAndAll_stopAtTheFirstFail() throws IOException {
    Verdict<String> later = new Verdict<>(new Scrutiny.Pass<>("c"));
    List<Intermediate<String>> elements = List.of(
        new Verdict<>(new Scrutiny.Mock<>()),
        new Verdict<>(new Scrutiny.Fail<>()),
        later);

    Scrutiny<List<String>> grouped = scrutinize(Intermediate.group(elements), scratch);
    Scrutiny<List<String>> all = scrutinize(Intermediate.all(elements), scratch);

    assertEquals(new Scrutiny.Fail<>(), grouped);
    assertEquals(new Scrutiny.Fail<>(), all);
    assertEquals(0, later.calls().get());
  }

  @Test
  void any_scrutinizesToTheFirstAlternativeThatPassesOrMocks() throws IOException {
    Verdict<String> later = new Verdict<>(new Scrutiny.Pass<>("later"));
    List<Intermediate<String>> passing = List.of(
        new Throwing<>(new IOException("mirror down")),
        new Verdict<>(new Scrutiny.Fail<>()),
        new Verdict<>(new Scrutiny.Pass<>("mirror")),
        later);
    List<Intermediate<String>> mocking = List.of(
        new Verdict<>(new Scrutiny.Fail<>()),
        new Verdict<>(new Scrutiny.Mock<>()),
        later);

    Scrutiny<String> passed = scrutinize(Intermediate.any(passing), scratch);
    Scrutiny<String> mocked = scrutinize(Intermediate.any(mocking), scratch);

    assertEquals(new Scrutiny.Pass<>("mirror"), passed);
    assertEquals(new Scrutiny.Mock<>(), mocked);
    assertEquals(0, later.calls().get());
  }

  @Test
  void any_fails_whenNoneSucceeds_andOneFailedWithoutThrowing() throws IOException {
    List<Intermediate<String>> alternatives = List.of(
        new Throwing<>(new IOException("first")),
        new Verdict<>(new Scrutiny.Fail<>()),
        new Throwing<>(new IOException("second")));

    Scrutiny<String> scrutiny = scrutinize(Intermediate.any(alternatives), scratch);

    assertEquals(new Scrutiny.Fail<>(), scrutiny);
  }

  @Test
  void any_throwsTheFirstFailure_withLaterFailuresSuppressed_whenEveryAlternativeThrows() {
    IOException first = new IOException("first");
    IOException second = new IOException("second");
    List<Intermediate<String>> alternatives = List.of(new Throwing<>(first), new Throwing<>(second));

    IOException thrown = assertThrows(IOException.class, () -> scrutinize(Intermediate.any(alternatives), scratch));

    assertSame(first, thrown);
    assertEquals(List.of(second), List.of(thrown.getSuppressed()));
  }

  private static String record(List<String> order, String value) {
    order.add(value);
    return value;
  }

  record Probe() implements Intermediate.Step<String> {

    @Override
    public String collapse(Context context) {
      return "probe";
    }
  }

  /**
   * A step that must not collapse, scrutinizing to a given verdict.
   *
   * @param verdict scrutiny to return
   * @param calls   scrutinize calls so far
   */
  record Verdict<T>(Scrutiny<T> verdict, AtomicInteger calls) implements Intermediate.Step<T> {

    Verdict(Scrutiny<T> verdict) {
      this(verdict, new AtomicInteger());
    }

    @Override
    public T collapse(Context context) {
      return fail("Scrutinized work must not collapse.");
    }

    @Override
    public Scrutiny<T> scrutinize(Context context) {
      calls.incrementAndGet();
      return verdict;
    }
  }

  /**
   * A step that must not collapse, throwing a given failure when scrutinized.
   *
   * @param failure exception to throw
   */
  record Throwing<T>(IOException failure) implements Intermediate.Step<T> {

    @Override
    public T collapse(Context context) {
      return fail("Scrutinized work must not collapse.");
    }

    @Override
    public Scrutiny<T> scrutinize(Context context) throws IOException {
      throw failure;
    }
  }
}
