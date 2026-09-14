package cat.tabbie.sdk.album;

import lombok.NonNull;

public record Reference(@NonNull String sha256, long size) {

  public Reference {
    if (!sha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Expected a lowercase SHA-256 hex digest.");
    }
    if (size < 0) {
      throw new IllegalArgumentException("Content size must be non-negative.");
    }
  }
}
