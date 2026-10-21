package cat.tabbie.sdk.album.model;

import java.io.IOException;

import cat.tabbie.sdk.album.repository.Describe;
import cat.tabbie.sdk.platform.Relative;
import lombok.NonNull;

//TODO: Documentation
public sealed interface Lease extends AutoCloseable {

  @Override
  void close() throws IOException;

  @NonNull
  Relative relative();

  non-sealed interface Read extends Lease {

    @NonNull
    Describe read() throws Refusal;
  }

  non-sealed interface Write extends Lease {

    void create(@NonNull Describe content) throws Refusal;

    void update(@NonNull Edit content) throws Refusal;

    void delete() throws Refusal;
  }

  @FunctionalInterface
  interface Edit {

    Describe edit(@NonNull Describe before) throws IOException;
  }
}
