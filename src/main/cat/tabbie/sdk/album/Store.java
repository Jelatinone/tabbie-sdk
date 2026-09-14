package cat.tabbie.sdk.album;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.NonNull;

public interface Store {

  Snapshot.Reference capture(InputStream source) throws IOException;

  InputStream open(Snapshot.Reference content) throws IOException;

  default Snapshot.Reference capture(@NonNull Path source) throws IOException {
    try (InputStream input = Files.newInputStream(source)) {
      return capture(input);
    }
  }

  default Snapshot.Reference capture(@NonNull byte[] bytes) throws IOException {
    return capture(new ByteArrayInputStream(bytes.clone()));
  }
}
