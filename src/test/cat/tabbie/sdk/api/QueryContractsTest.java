package cat.tabbie.sdk.api;

import static cat.tabbie.sdk.TestFixtures.collapse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import cat.tabbie.sdk.album.revision.Intermediate;

class QueryContractsTest {

  @TempDir
  Path scratch;

  @Test
  void several_requiresAPositiveLimit_whenOneIsGiven() {
    assertEquals(new Query.Several<>("criteria", null), new Query.Several<>("criteria"));
    assertEquals(3, new Query.Several<>("criteria", 3).limit());
    assertThrows(IllegalArgumentException.class, () -> new Query.Several<>("criteria", 0));
    assertThrows(NullPointerException.class, () -> new Query.Singular<>(null));
  }

  @Test
  void defaults_deriveExistenceCountAndSingleItems_fromSeveral_onlyWhenCollapsed() throws IOException {
    List<Query.Several<String>> received = new ArrayList<>();
    Queryable<String, String> names = query -> Intermediate.of(context -> {
      received.add(query);
      return List.of("alpha", "beta");
    });

    Intermediate<Boolean> exists = names.query(new Query.Exists<>("a"));
    Intermediate<Long> count = names.query(new Query.Count<>("a"));
    Intermediate<Optional<String>> first = names.query(new Query.Singular<>("a"));

    assertEquals(List.of(), received);
    assertEquals(true, collapse(exists, scratch));
    assertEquals(2L, collapse(count, scratch));
    assertEquals(Optional.of("alpha"), collapse(first, scratch));
    assertEquals(List.of(new Query.Several<>("a", 1), new Query.Several<>("a"), new Query.Several<>("a", 1)), received);
  }

  @Test
  void defaults_reportNoMatches_andFailUnanswerableRequests() throws IOException {
    Queryable<String, String> empty = query -> Intermediate.of(List.of());
    Queryable<String, String> unavailable = query -> Intermediate.of(context -> {
      throw new IOException("Unavailable");
    });

    assertEquals(false, collapse(empty.query(new Query.Exists<>("a")), scratch));
    assertEquals(0L, collapse(empty.query(new Query.Count<>("a")), scratch));
    assertEquals(Optional.empty(), collapse(empty.query(new Query.Singular<>("a")), scratch));
    assertThrows(IOException.class, () -> collapse(unavailable.query(new Query.Exists<>("a")), scratch));
    assertThrows(IOException.class, () -> collapse(unavailable.query(new Query.Count<>("a")), scratch));
    assertThrows(IOException.class, () -> collapse(unavailable.query(new Query.Singular<>("a")), scratch));
  }

  @Test
  void criteria_selectByIdentityOrDuration() {
    Criteria<String> byIdentity = Criteria.identifier("id");
    Criteria<String> byDuration = Criteria.duration(Duration.ofSeconds(5));

    assertEquals(Optional.of("id"), byIdentity.identifier());
    assertEquals(Optional.empty(), byIdentity.duration());
    assertEquals(Optional.empty(), byDuration.identifier());
    assertEquals(Optional.of(Duration.ofSeconds(5)), byDuration.duration());
  }

  @Test
  void observerNone_isSharedAndIgnoresEverything() {
    Observer<String, Integer> none = Observer.none();

    none.transfer(1);
    none.verified("done");
    none.failed(new Exception("ignored"));

    assertSame(Observer.NONE, none);
  }

  @Test
  void response_resolvesKnownStatusCodes() {
    assertSame(Response.OK, Response.from(200));
    assertSame(Response.NOT_FOUND, Response.from(404));
    assertEquals(429, Response.TOO_MANY_REQUESTS.getStatusCode());
    assertEquals(Response.values().length, Arrays.stream(Response.values()).map(Response::getStatusCode).distinct()
        .count());
    assertThrows(IllegalArgumentException.class, () -> Response.from(299));
  }
}
