package cat.tabbie.sdk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.addon.artifact.Behaviourpack;
import cat.tabbie.sdk.addon.artifact.Datapack;
import cat.tabbie.sdk.addon.artifact.Mod;
import cat.tabbie.sdk.addon.artifact.Modpack;
import cat.tabbie.sdk.addon.artifact.Plugin;
import cat.tabbie.sdk.addon.artifact.Resourcepack;
import cat.tabbie.sdk.album.Reference;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.minecraft.Distribution;
import cat.tabbie.sdk.minecraft.Environment;
import cat.tabbie.sdk.minecraft.Label;
import cat.tabbie.sdk.minecraft.Version;

public final class Fixtures {
  private Fixtures() {
  }

  public static Stream<Class<? extends Artifact>> families() {
    return Stream.of(Mod.class, Plugin.class, Datapack.class, Behaviourpack.class,
        Resourcepack.Java.class, Resourcepack.Bedrock.class, Modpack.class);
  }

  public static Label label(Distribution distribution) {
    Version version = distribution instanceof Distribution.Java
        ? new Version.Java("1.21", Version.Java.Release.RELEASE, Instant.EPOCH)
        : new Version.Bedrock("1.21", Version.Bedrock.Release.RELEASE, Instant.EPOCH);
    return new Label(version, distribution, Environment.SERVER);
  }

  public static Label target(Class<? extends Artifact> family) {
    if (family == Plugin.class)
      return label(Distribution.Java.Of.PAPER);
    if (family == Behaviourpack.class || family == Resourcepack.Bedrock.class) {
      return label(Distribution.Bedrock.Of.BEDROCK_NATIVE);
    }
    return label(Distribution.Java.Of.FABRIC);
  }

  public static Artifact artifact(Class<? extends Artifact> family, boolean custom,
      Identity<Artifact> id, String name, Store source, Set<Label> labels,
      Set<Identity<Artifact>> depends, Set<Identity<Artifact>> conflicts, Artifact.Artist<Artifact> artist)
      throws Exception {
    Class<?> type = Stream.of(family.getDeclaredClasses())
        .filter(candidate -> candidate.getSimpleName().equals(custom ? "Custom" : "Default")).findFirst().orElseThrow();
    Object[] args = custom ? new Object[] { id, name, source, labels, depends, conflicts, artist }
        : new Object[] { id, name, source, labels, depends, conflicts };
    try {
      return (Artifact) type.getConstructors()[0].newInstance(args);
    } catch (InvocationTargetException failure) {
      throw (Exception) failure.getCause();
    }
  }

  public static byte[] zip(String... namesAndContent) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      for (int i = 0; i < namesAndContent.length; i += 2) {
        zip.putNextEntry(new ZipEntry(namesAndContent[i]));
        zip.write(namesAndContent[i + 1].getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return bytes.toByteArray();
  }

  public static Map<String, String> snapshot(Path root) throws IOException {
    Map<String, String> snapshot = new LinkedHashMap<>();
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted().toList()) {
        snapshot.put(root.relativize(path).toString(), Files.isDirectory(path) ? "directory"
            : Reference.of(path.getFileName().toString(), Files.readAllBytes(path)).sha256());
      }
    }
    return snapshot;
  }

  public static class Source implements Store {
    public final byte[] content;
    public final String fileName;
    public int opens;
    public int streamCloses;
    public int storeCloses;
    public boolean local;
    public IOException openFailure;
    public IOException closeFailure;

    public Source(byte[] content) {
      this("example.bin", content);
    }

    public Source(String fileName, byte[] content) {
      this.fileName = fileName;
      this.content = content.clone();
    }

    @Override
    public InputStream open() throws IOException {
      opens++;
      if (openFailure != null)
        throw openFailure;
      return new ByteArrayInputStream(content) {
        @Override
        public void close() throws IOException {
          streamCloses++;
          if (closeFailure != null)
            throw closeFailure;
        }
      };
    }

    @Override
    public boolean exists() {
      return local;
    }

    @Override
    public void close() {
      storeCloses++;
    }
  }

  public static class Retention implements Store {
    public final Map<String, byte[]> contents = new HashMap<>();
    public int captures;
    public int opens;
    public int storeCloses;
    public int failAt = Integer.MAX_VALUE;
    public String defaultName = "retention-default.bin";
    public InputStream lastInput;

    @Override
    public InputStream open() throws IOException {
      opens++;
      throw new IOException("Retention has no primary source.");
    }

    @Override
    public boolean exists() {
      return false;
    }

    @Override
    public Reference capture(InputStream input, Observer observer) throws IOException {
      return capture(defaultName, input, observer);
    }

    public Reference capture(String name, InputStream input, Observer observer) throws IOException {
      Reference.validateFilename(name);
      lastInput = input;
      captures++;
      try {
        if (captures >= failAt)
          throw new IOException("Injected capture failure.");
        byte[] bytes = input.readAllBytes();
        observer.transferred(bytes.length, OptionalLong.empty());
        Reference reference = Reference.of(name, bytes);
        contents.putIfAbsent(reference.sha256(), bytes);
        observer.verified(reference);
        return reference;
      } catch (IOException failure) {
        observer.failed(failure);
        throw failure;
      }
    }

    public boolean exists(Reference reference) {
      byte[] bytes = contents.get(reference.sha256());
      return bytes != null && Reference.of(reference.fileName(), bytes).equals(reference);
    }

    public InputStream open(Reference reference) throws IOException {
      if (!exists(reference))
        throw new IOException("Missing or corrupt retained content.");
      return new ByteArrayInputStream(contents.get(reference.sha256()));
    }

    @Override
    public void close() {
      storeCloses++;
    }
  }
}
