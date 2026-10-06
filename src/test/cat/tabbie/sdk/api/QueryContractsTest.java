package cat.tabbie.sdk.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryContractsTest {

	@Test
	void several_requiresAPositiveLimit_whenOneIsGiven() {
		assertEquals(null, new Query.Several<>("criteria").limit());
		assertEquals(3, new Query.Several<>("criteria", 3).limit());
		assertThrows(IllegalArgumentException.class, () -> new Query.Several<>("criteria", 0));
		assertThrows(NullPointerException.class, () -> new Query.Singular<>(null));
	}

	@Test
	void defaults_deriveExistenceCountAndSingleItems_fromSeveral() {
		List<Query.Several<String>> received = new ArrayList<>();
		Queryable<String, String> names = query -> {
			received.add(query);
			return Optional.of(List.of("alpha", "beta"));
		};

		Optional<Boolean> exists = names.query(new Query.Exists<>("a"));
		Optional<Long> count = names.query(new Query.Count<>("a"));
		Optional<String> first = names.query(new Query.Singular<>("a"));

		assertEquals(Optional.of(true), exists);
		assertEquals(Optional.of(2L), count);
		assertEquals(Optional.of("alpha"), first);
		assertEquals(List.of(new Query.Several<>("a"), new Query.Several<>("a"), new Query.Several<>("a")), received);
	}

	@Test
	void defaults_distinguishNoMatches_fromUnanswerableRequests() {
		Queryable<String, String> empty = query -> Optional.of(List.of());
		Queryable<String, String> unavailable = query -> Optional.empty();

		assertEquals(Optional.of(false), empty.query(new Query.Exists<>("a")));
		assertEquals(Optional.of(0L), empty.query(new Query.Count<>("a")));
		assertEquals(Optional.empty(), empty.query(new Query.Singular<>("a")));
		assertEquals(Optional.empty(), unavailable.query(new Query.Exists<>("a")));
		assertEquals(Optional.empty(), unavailable.query(new Query.Count<>("a")));
	}

	@Test
	void overriddenCount_isUsedForExistence() {
		Queryable<String, String> counted = new Queryable<>() {

			@Override
			public Optional<Long> query(Query.Count<String> query) {
				return Optional.of(1L);
			}

			@Override
			public Optional<Collection<String>> query(Query.Several<String> query) {
				throw new AssertionError("Existence must use the overridden count.");
			}
		};

		assertEquals(Optional.of(true), counted.query(new Query.Exists<>("a")));
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
