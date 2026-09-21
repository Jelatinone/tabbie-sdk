package cat.tabbie.sdk.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import cat.tabbie.sdk.Identity;

class QueryIdentityTest {
  @Test void derivesCountsAndExistenceAndPreservesAbsenceVersusFailure() {
    Queryable<String, String> catalog = new Queryable<>() {
      @Override public Optional<String> query(Query.Singular<String> query) {
        return query(new Query.Several<>(query.criteria(), 1)).stream().findFirst();
      }
      @Override public Collection<String> query(Query.Several<String> query) {
        if (query.criteria().equals("failure")) throw new IllegalStateException("provider unavailable");
        return query.criteria().equals("missing") ? List.of()
            : List.of("first", "second").stream().limit(query.limit() == null ? Long.MAX_VALUE : query.limit()).toList();
      }
    };
    assertEquals(Optional.of(2L), catalog.query(new Query.Count<>("all")));
    assertEquals(Optional.of(true), catalog.query(new Query.Exists<>("all")));
    assertEquals(Optional.of(false), catalog.query(new Query.Exists<>("missing")));
    assertEquals(Optional.empty(), catalog.query(new Query.Singular<>("missing")));
    assertEquals(List.of("first"), catalog.query(new Query.Several<>("all", 1)));
    assertThrows(IllegalStateException.class, () -> catalog.query(new Query.Exists<>("failure")));
    assertThrows(IllegalStateException.class, () -> catalog.query(new Query.Count<>("failure")));
    assertThrows(IllegalArgumentException.class, () -> new Query.Several<>("all", 0));
    assertThrows(IllegalArgumentException.class, () -> new Query.Several<>("all", -1));
    assertThrows(NullPointerException.class, () -> new Query.Singular<>(null));
    assertThrows(NullPointerException.class, () -> new Query.Count<>(null));
    assertThrows(NullPointerException.class, () -> new Query.Exists<>(null));
    assertThrows(NullPointerException.class, () -> new Query.Several<>(null));
  }

  @Test void doesNotTurnUnavailableCountsIntoFalse() {
    Queryable<String, String> unavailable = new Queryable<>() {
      @Override public Optional<Long> query(Query.Count<String> query) { return Optional.empty(); }
      @Override public Optional<String> query(Query.Singular<String> query) { return Optional.empty(); }
      @Override public Collection<String> query(Query.Several<String> query) { throw new AssertionError("Must use count override"); }
    };
    assertEquals(Optional.empty(), unavailable.query(new Query.Exists<>("all")));
  }

  @Test void identitiesUseStableUtf8NamespacedInputsAndCriteriaPreserveSelections() {
    String canonical = "provider:project:build:payload-\u2603";
    Identity<Object> identity = Identity.create(canonical);
    assertEquals(UUID.nameUUIDFromBytes(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)), identity.id());
    assertEquals(identity, Identity.create(canonical));
    assertEquals(identity, Identity.create(identity.id()));
    assertNotEquals(identity, Identity.create("another:" + canonical));
    assertEquals(Optional.of(identity), Criteria.identifier(identity).identifier());
    assertTrue(Criteria.identifier(identity).duration().isEmpty());
    assertEquals(Optional.of(Duration.ofSeconds(5)), Criteria.duration(Duration.ofSeconds(5)).duration());
    assertTrue(Criteria.duration(Duration.ZERO).identifier().isEmpty());
    assertThrows(NullPointerException.class, () -> Criteria.identifier(null));
    assertThrows(NullPointerException.class, () -> Criteria.duration(null));
  }
}
