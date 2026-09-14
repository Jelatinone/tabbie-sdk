package cat.tabbie.sdk.album;

import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cat.tabbie.sdk.album.Snapshot.Metadata.Value.*;
import lombok.NonNull;

public record Snapshot(@NonNull Reference reference, @NonNull Metadata metadata) {

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

  public record Metadata(@NonNull Map<String, State> attributes) {

    public static final String POSIX_MODE = "posix:mode";
    public static final String OWNER = "owner:principal";
    public static final String GROUP = "posix:group";
    public static final String ACL = "acl:entries";
    public static final String MODIFIED = "basic:lastModifiedTime";
    public static final String ACCESSED = "basic:lastAccessTime";
    public static final String CREATED = "basic:creationTime";
    public static final String DOS_READ_ONLY = "dos:readonly";
    public static final String DOS_HIDDEN = "dos:hidden";
    public static final String DOS_SYSTEM = "dos:system";
    public static final String DOS_ARCHIVE = "dos:archive";

    public Metadata {
      attributes = Map.copyOf(attributes);
      attributes.forEach((key, state) -> {
        if (key.indexOf(':') <= 0 || key.endsWith(":") || key.indexOf('\0') >= 0) {
          throw new IllegalArgumentException("Attribute keys must be namespaced: " + key);
        }
        if (state instanceof State.Present present) {
          Class<? extends Value> expected = switch (key) {
            case POSIX_MODE -> Mode.class;
            case OWNER, GROUP -> Principal.class;
            case ACL -> Accessors.class;
            case MODIFIED, ACCESSED, CREATED -> Timestamp.class;
            case DOS_READ_ONLY, DOS_HIDDEN, DOS_SYSTEM, DOS_ARCHIVE -> Flag.class;
            default -> Value.class;
          };
          if (!expected.isInstance(present.value())) {
            throw new IllegalArgumentException("Wrong value type for " + key);
          }
        }
      });
    }

    public static Metadata none() {
      return new Metadata(Map.of());
    }

    public Metadata with(@NonNull String key, @NonNull Value value) {
      return withState(key, new State.Present(value));
    }

    public Metadata absent(@NonNull String key) {
      return withState(key, new State.Absent());
    }

    public Metadata withState(@NonNull String key, @NonNull State state) {
      Map<String, State> copy = new LinkedHashMap<>(attributes);
      copy.put(key, state);
      return new Metadata(copy);
    }

    public static void requireSameKeys(@NonNull Metadata before, @NonNull Metadata after) {
      if (!before.attributes().keySet().equals(after.attributes().keySet())) {
        throw new IllegalArgumentException(
            "Describe both sides of every tracked attribute; use explicit Absent where appropriate.");
      }
    }

    public sealed interface State {
      public record Absent() implements State {
      }

      public record Present(@NonNull Value value) implements State {
      }
    }

    public sealed interface Value {
      public record Text(@NonNull String value) implements Value {

      }

      public record Integer(@NonNull long value) implements Value {
      }

      public record Flag(@NonNull boolean value) implements Value {
      }

      public record Timestamp(@NonNull Instant value) implements Value {
      }

      public record Mode(int bits) implements Value {
        public Mode {
          if (bits < 0 || bits > 07777) {
            throw new IllegalArgumentException("Mode must be between 0000 and 07777.");
          }
        }
      }

      public record Principal(@NonNull String authority, @NonNull String id) implements Value {
        public Principal {
          if (authority.isBlank() || id.isBlank()) {
            throw new IllegalArgumentException("Principal authority and ID are required.");
          }
        }
      }

      public record Accessors(@NonNull List<Accessor> entries) implements Value {
        public Accessors {
          entries = List.copyOf(entries);
        }
      }

      public record Accessor(
          Principal principal,
          AclEntryType type,
          Set<AclEntryPermission> permissions,
          Set<AclEntryFlag> flags) {

        public Accessor {
          permissions = Set.copyOf(permissions);
          flags = Set.copyOf(flags);
        }
      }

      public record Binary(@NonNull Reference content) implements Value {
      }
    }
  }
}