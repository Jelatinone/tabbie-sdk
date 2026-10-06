package cat.tabbie.sdk.album.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import cat.tabbie.sdk.TestFixtures.MockRetention;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.api.Observer;

import static cat.tabbie.sdk.TestFixtures.bytes;
import static cat.tabbie.sdk.TestFixtures.collapse;
import static cat.tabbie.sdk.TestFixtures.source;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryContractsTest {

	private static final String ABC_SHA256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

	@TempDir
	Path scratch;

	@Test
	void pending_acceptsCanonicalPathsEndingInTheFilename() {
		Reference.Pending nested = new Reference.Pending("pack.mcmeta", "assets/pack.mcmeta", ABC_SHA256, 3L);
		Reference.Pending topLevel = new Reference.Pending("example.jar");

		assertEquals("assets/pack.mcmeta", nested.path());
		assertEquals("example.jar", topLevel.path());
		assertEquals(null, topLevel.expectedSha256());
		assertEquals(null, topLevel.expectedSize());
	}

	@Test
	void pending_rejectsMismatchedOrNonCanonicalPaths_andMalformedEvidence() {
		assertThrows(IllegalArgumentException.class, () -> new Reference.Pending("a.txt", "dir/b.txt", null, null));
		assertThrows(IllegalArgumentException.class, () -> new Reference.Pending("a.txt", "dir//a.txt", null, null));
		assertThrows(IllegalArgumentException.class, () -> new Reference.Pending("a.txt", "./a.txt", null, null));
		assertThrows(IllegalArgumentException.class, () -> new Reference.Pending("a.txt", "dir\\a.txt", null, null));
		assertThrows(IllegalArgumentException.class, () -> new Reference.Pending("a.txt", ABC_SHA256.toUpperCase(), 3L));
		assertThrows(IllegalArgumentException.class, () -> new Reference.Pending("a.txt", "abc", 3L));
		assertThrows(IllegalArgumentException.class, () -> new Reference.Pending("a.txt", null, -1L));
		assertThrows(IllegalArgumentException.class, () -> new Reference.Pending("dir/a.txt"));
	}

	@Test
	void captured_requiresASingleFilename_aLowercaseDigest_andANonNegativeSize() {
		assertThrows(IllegalArgumentException.class, () -> new Reference.Captured("dir/a.txt", ABC_SHA256, 3L));
		assertThrows(IllegalArgumentException.class, () -> new Reference.Captured("..", ABC_SHA256, 3L));
		assertThrows(IllegalArgumentException.class, () -> new Reference.Captured(" ", ABC_SHA256, 3L));
		assertThrows(IllegalArgumentException.class, () -> new Reference.Captured("a.txt", ABC_SHA256.toUpperCase(), 3L));
		assertThrows(IllegalArgumentException.class, () -> new Reference.Captured("a.txt", ABC_SHA256, -1L));
		assertThrows(NullPointerException.class, () -> new Reference.Captured(null, ABC_SHA256, 3L));
	}

	@Test
	void of_identifiesBytes_withoutModifyingThem() {
		byte[] content = bytes("abc");

		Reference.Captured reference = Reference.of("a.txt", content);

		assertEquals(new Reference.Captured("a.txt", ABC_SHA256, 3L), reference);
		assertArrayEquals(bytes("abc"), content);
		assertEquals(new Reference.Pending("a.txt", ABC_SHA256, 3L), reference.expected());
	}

	@Test
	void transfer_copiesAndIdentifiesBytes_reportingProgressAndVerification() throws IOException {
		Recorder observer = new Recorder();
		ByteArrayOutputStream sink = new ByteArrayOutputStream();
		TrackedInput input = new TrackedInput(bytes("abc"));

		Reference.Captured captured = Extract.transfer(new Reference.Pending("a.txt", ABC_SHA256, 3L), input, sink,
				observer);

		assertEquals(new Reference.Captured("a.txt", ABC_SHA256, 3L), captured);
		assertArrayEquals(bytes("abc"), sink.toByteArray());
		assertEquals(List.of(new Extract.Transfer(3L, OptionalLong.of(3L))), observer.transfers);
		assertEquals(List.of(captured), observer.verified);
		assertFalse(input.closed, "transfer leaves the source open");
	}

	@Test
	void transfer_rejectsBytesThatContradictExpectedEvidence() {
		Recorder sizeObserver = new Recorder();
		Recorder digestObserver = new Recorder();

		assertThrows(IOException.class, () -> Extract.transfer(new Reference.Pending("a.txt", null, 4L),
				new ByteArrayInputStream(bytes("abc")), new ByteArrayOutputStream(), sizeObserver));
		assertThrows(IOException.class, () -> Extract.transfer(new Reference.Pending("a.txt", "0".repeat(64), null),
				new ByteArrayInputStream(bytes("abc")), new ByteArrayOutputStream(), digestObserver));
		assertEquals(1, sizeObserver.failures.size());
		assertEquals(1, digestObserver.failures.size());
		assertTrue(sizeObserver.verified.isEmpty());
	}

	@Test
	void transferProgress_rejectsNegativeCounts() {
		assertThrows(IllegalArgumentException.class, () -> new Extract.Transfer(-1L, OptionalLong.empty()));
		assertThrows(IllegalArgumentException.class, () -> new Extract.Transfer(0L, OptionalLong.of(-1L)));
	}

	@Test
	void captureOfAStream_consumesItOnce_andLeavesItOpen() throws IOException {
		MockRetention retention = new MockRetention();
		TrackedInput input = new TrackedInput(bytes("abc"));

		Intermediate<Reference.Captured> capture = retention.capture(new Reference.Pending("a.txt"), input,
				Observer.none());
		Reference.Captured captured = collapse(capture, scratch);

		assertEquals(ABC_SHA256, captured.sha256());
		assertFalse(input.closed);
		assertThrows(IOException.class, () -> collapse(capture, scratch));
	}

	@Test
	void captureOfBytes_takesADefensiveCopy() throws IOException {
		MockRetention retention = new MockRetention();
		byte[] content = bytes("abc");

		Intermediate<Reference.Captured> capture = retention.capture(new Reference.Pending("a.txt"), content);
		content[0] = 'z';
		Reference.Captured captured = collapse(capture, scratch);

		assertEquals(ABC_SHA256, captured.sha256());
		assertArrayEquals(bytes("abc"), retention.read(captured));
	}

	@Test
	void capture_readsNothingUntilCollapsed_andRetainedBytesReopenByReference() throws IOException {
		MockRetention retention = new MockRetention();
		var source = source("a.txt", bytes("abc"));

		Intermediate<Reference.Captured> capture = retention.capture(source);
		int opensBeforeCollapse = source.opens();
		Reference.Captured captured = collapse(capture, scratch);
		Describe retained = retention.retained(captured);

		assertEquals(0, opensBeforeCollapse);
		assertTrue(retention.exists(captured));
		assertEquals(captured.expected(), retained.of());
		try (InputStream first = retained.open(); InputStream second = retained.open()) {
			assertArrayEquals(bytes("abc"), first.readAllBytes());
			assertArrayEquals(bytes("abc"), second.readAllBytes());
		}
	}

	@Test
	void defaults_reportNothingRetained_andStoresNeedNoCleanup() throws IOException {
		Store store = new MinimalStore();

		assertFalse(store.exists());
		assertFalse(store.exists(Reference.of("a.txt", bytes("abc"))));
		store.close();
	}

	private static final class Recorder implements Observer<Reference.Captured, Extract.Transfer> {

		final List<Extract.Transfer> transfers = new ArrayList<>();

		final List<Reference.Captured> verified = new ArrayList<>();

		final List<Exception> failures = new ArrayList<>();

		@Override
		public void transfer(Extract.Transfer transfer) {
			transfers.add(transfer);
		}

		@Override
		public void verified(Reference.Captured captured) {
			verified.add(captured);
		}

		@Override
		public void failed(Exception failure) {
			failures.add(failure);
		}
	}

	private static final class TrackedInput extends ByteArrayInputStream {

		boolean closed;

		TrackedInput(byte[] content) {
			super(content);
		}

		@Override
		public void close() throws IOException {
			closed = true;
			super.close();
		}
	}

	private static final class MinimalStore implements Store {

		@Override
		public Reference.Pending of() {
			return new Reference.Pending("a.txt");
		}

		@Override
		public InputStream open() {
			return new ByteArrayInputStream(bytes("abc"));
		}

		@Override
		public Intermediate<Reference.Captured> capture(Describe source,
				Observer<? super Reference.Captured, ? super Transfer> observer) {
			return Intermediate.of(context -> {
				throw new IOException("Not retained.");
			});
		}

		@Override
		public Describe retained(Reference.Captured reference) {
			return this;
		}
	}
}
