package cat.tabbie.sdk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.addon.artifact.Mod;
import cat.tabbie.sdk.album.repository.Describe;
import cat.tabbie.sdk.album.repository.Extract;
import cat.tabbie.sdk.album.repository.Reference;
import cat.tabbie.sdk.album.revision.Intermediate;
import cat.tabbie.sdk.api.Observer;
import cat.tabbie.sdk.merchant.Provider;
import cat.tabbie.sdk.merchant.Provider.Coordinate;
import cat.tabbie.sdk.minecraft.Environment;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Parity;
import cat.tabbie.sdk.minecraft.Version;
import cat.tabbie.sdk.minecraft.distribution.Bedrock;
import cat.tabbie.sdk.minecraft.distribution.Java;
import cat.tabbie.sdk.platform.Platform;
import cat.tabbie.sdk.platform.Relative;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

/**
 * Shared values, builders, and in-memory fakes for SDK tests. Everything here
 * uses only the public SDK API.
 */
public final class TestFixtures {

	public static final Identity<Provider<?, ?>> PROVIDER_ID = Identity.create("test:provider");

	public static final Coordinate.Project PROJECT = new Coordinate.Project(PROVIDER_ID, "example");

	public static final Coordinate.Channel CHANNEL = PROJECT.channel("release");

	public static final Coordinate.Build BUILD = CHANNEL.build("1.0.0");

	public static final Version.Java JAVA_VERSION = new Version.Java("1.21.1", Version.Java.Release.RELEASE);

	public static final Version.Bedrock BEDROCK_VERSION = new Version.Bedrock("1.21.80", Version.Bedrock.Release.RELEASE);

	public static final Label VANILLA_CLIENT = new Label(JAVA_VERSION, Java.Of.NATIVE, Environment.CLIENT);

	public static final Label VANILLA_SERVER = new Label(JAVA_VERSION, Java.Of.NATIVE, Environment.SERVER);

	public static final Label FABRIC_CLIENT = new Label(JAVA_VERSION, Java.Launcher.Of.FABRIC, Environment.CLIENT);

	public static final Label FABRIC_SERVER = new Label(JAVA_VERSION, Java.Launcher.Of.FABRIC, Environment.SERVER);

	public static final Label QUILT_CLIENT = new Label(JAVA_VERSION, Java.Launcher.Of.QUILT, Environment.CLIENT);

	public static final Label PAPER_SERVER = new Label(JAVA_VERSION, Java.Manager.Of.PAPER, Environment.SERVER);

	public static final Label BEDROCK_CLIENT = new Label(BEDROCK_VERSION, Bedrock.Of.NATIVE, Environment.CLIENT);

	public static final Label BEDROCK_SERVER = new Label(BEDROCK_VERSION, Bedrock.Of.NATIVE, Environment.SERVER);

	public static final Label ENDSTONE_SERVER = new Label(BEDROCK_VERSION, Bedrock.Manager.Of.ENDSTONE, Environment.SERVER);

	public static final Platform PLATFORM = new Platform(Platform.Kernel.LINUX, Platform.Architecture.X_64);

	public static final Relative.World WORLD = Relative.world("world");

	private TestFixtures() {
	}

	/**
	 * Encodes text as UTF-8.
	 */
	public static byte[] bytes(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Addresses a file of the shared build.
	 */
	public static Coordinate.File file(String key) {
		return BUILD.file(key);
	}

	/**
	 * Describes a top-level file without expected evidence.
	 */
	public static MockSource source(String fileName, byte[] content) {
		return new MockSource(new Reference.Pending(fileName), content);
	}

	/**
	 * Describes a mod jar supporting the given labels.
	 */
	public static Mod.Default mod(String key, Set<Label> labels, Set<Artifact.Relation> relations) {
		return new Mod.Default(file(key), key, source(key + ".jar", bytes(key)), labels, Parity.UNKNOWN, relations);
	}

	/**
	 * Describes a mod jar supporting the given labels, without relations.
	 */
	public static Mod.Default mod(String key, Label... labels) {
		return mod(key, Set.of(labels), Set.of());
	}

	/**
	 * Creates an artifact context at the installation root with the shared
	 * world mount.
	 */
	public static Artifact.Context.Default context(Label label, Extract repository) {
		return new Artifact.Context.Default(label, PLATFORM, repository, Relative.root(), WORLD);
	}

	/**
	 * Collapses work with a scratch directory and no observer.
	 */
	public static <T> T collapse(Intermediate<T> work, Path scratch) throws IOException {
		return work.collapse(new Intermediate.Step.Context.Default(scratch, Observer.none()));
	}

	/**
	 * Builds a ZIP archive of text entries in insertion order. Names ending in a
	 * slash become directory entries.
	 */
	public static byte[] zip(Map<String, String> entries) {
		return zip(entries, ZipEntry.DEFLATED);
	}

	/**
	 * Builds a ZIP archive of text entries in insertion order with one
	 * compression method. Names ending in a slash become directory entries.
	 */
	public static byte[] zip(Map<String, String> entries, int method) {
		try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				ZipOutputStream zip = new ZipOutputStream(buffer)) {
			for (Map.Entry<String, String> entry : entries.entrySet()) {
				byte[] content = bytes(entry.getValue());
				ZipEntry zipEntry = new ZipEntry(entry.getKey());
				zipEntry.setMethod(method);
				if (method == ZipEntry.STORED) {
					CRC32 crc = new CRC32();
					crc.update(content);
					zipEntry.setSize(content.length);
					zipEntry.setCompressedSize(content.length);
					zipEntry.setCrc(crc.getValue());
				}
				zip.putNextEntry(zipEntry);
				zip.write(content);
				zip.closeEntry();
			}
			zip.finish();
			return buffer.toByteArray();
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	/**
	 * Orders entries for {@link #zip(Map)}.
	 */
	public static Map<String, String> entries(String... namesAndContents) {
		Map<String, String> entries = new LinkedHashMap<>();
		for (int index = 0; index < namesAndContents.length; index += 2) {
			entries.put(namesAndContents[index], namesAndContents[index + 1]);
		}
		return entries;
	}

	/**
	 * A reusable in-memory source that counts how often it is opened.
	 */
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	public static final class MockSource implements Describe {

		Reference.Pending expected;

		byte[] content;

		AtomicInteger opens = new AtomicInteger();

		public MockSource(Reference.Pending expected, byte[] content) {
			this.expected = expected;
			this.content = content.clone();
		}

		@Override
		public Reference.Pending of() {
			return expected;
		}

		@Override
		public InputStream open() {
			opens.incrementAndGet();
			return new ByteArrayInputStream(content);
		}

		public int opens() {
			return opens.get();
		}
	}

	/**
	 * An in-memory retention backend keyed by digest, which retains only verified
	 * captures.
	 */
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	public static final class MockRetention implements Extract {

		Map<String, byte[]> retained = new HashMap<>();

		AtomicInteger captures = new AtomicInteger();

		@Override
		public Intermediate<Reference.Captured> capture(Describe source,
				Observer<? super Reference.Captured, ? super Transfer> observer) {
			return Intermediate.of(context -> {
				ByteArrayOutputStream sink = new ByteArrayOutputStream();
				Reference.Captured captured;
				try (InputStream input = source.open()) {
					captured = Extract.transfer(source.of(), input, sink, observer);
				}
				retained.put(captured.sha256(), sink.toByteArray());
				captures.incrementAndGet();
				return captured;
			});
		}

		@Override
		public Describe retained(Reference.Captured reference) {
			return new Describe() {

				@Override
				public Reference.Pending of() {
					return reference.expected();
				}

				@Override
				public InputStream open() throws IOException {
					byte[] content = retained.get(reference.sha256());
					if (content == null) {
						throw new IOException("Not retained: " + reference);
					}
					return new ByteArrayInputStream(content);
				}
			};
		}

		@Override
		public boolean exists(Reference.Captured reference) {
			return retained.containsKey(reference.sha256());
		}

		public byte[] read(Reference.Captured reference) throws IOException {
			try (InputStream input = retained(reference).open()) {
				return input.readAllBytes();
			}
		}

		public int captures() {
			return captures.get();
		}
	}
}
