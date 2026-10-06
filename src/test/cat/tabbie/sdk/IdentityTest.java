package cat.tabbie.sdk;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityTest {

	@Test
	void create_wrapsAnExistingUuid() {
		UUID id = UUID.randomUUID();

		Identity<String> identity = Identity.create(id);

		assertEquals(id, identity.id());
		assertThrows(NullPointerException.class, () -> Identity.create((UUID) null));
	}

	@Test
	void create_derivesTheSameIdentity_fromTheSameText() {
		Identity<String> first = Identity.create("tabbie:example");
		Identity<String> again = Identity.create("tabbie:example");

		assertEquals(first, again);
		assertEquals(UUID.nameUUIDFromBytes("tabbie:example".getBytes(StandardCharsets.UTF_8)),
				first.id());
		assertNotEquals(first, Identity.create("tabbie:other"));
	}
}
