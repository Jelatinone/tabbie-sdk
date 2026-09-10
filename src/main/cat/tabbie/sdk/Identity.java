package cat.tabbie.sdk;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import lombok.NonNull;

/**
 * 
 * <h1>Identity</h1>
 * 
 * <p>
 * A stable UUID reference to a given resource.
 * </p>
 */
public sealed interface Identity {

	@NonNull
	UUID id();

	public static UUID identity(String canonical) {
		return UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8));
	}

	// TODO: Identity is useful because we can reference an actual instance of a
	// UUID with context of what the UUID is at all times and pass it around safely,
	// but implementing it this way is very verbose and boilerplate heavy.

	public record AddonIdentity(@NonNull UUID id) implements Identity {
		public static AddonIdentity create(@NonNull UUID id) {
			return new AddonIdentity(id);
		}

		public static AddonIdentity create(@NonNull String canonical) {
			return new AddonIdentity(identity(canonical));
		}
	}

	public record DistributionIdentity(@NonNull UUID id) implements Identity {
		public static DistributionIdentity create(@NonNull UUID id) {
			return new DistributionIdentity(id);
		}

		public static DistributionIdentity create(@NonNull String canonical) {
			return new DistributionIdentity(identity(canonical));
		}
	}

	public record ProviderIdentity(@NonNull UUID id) implements Identity {
		public static ProviderIdentity create(@NonNull UUID id) {
			return new ProviderIdentity(id);
		}

		public static ProviderIdentity create(@NonNull String canonical) {
			return new ProviderIdentity(identity(canonical));
		}
	}

	public record ReleaseIdentity(@NonNull UUID id) implements Identity {
		public static ReleaseIdentity create(@NonNull UUID id) {
			return new ReleaseIdentity(id);
		}

		public static ReleaseIdentity create(@NonNull String canonical) {
			return new ReleaseIdentity(identity(canonical));
		}
	}

	public record ArtifactIdentity(@NonNull UUID id) implements Identity {
		public static ArtifactIdentity create(@NonNull UUID id) {
			return new ArtifactIdentity(id);
		}

		public static ArtifactIdentity create(@NonNull String canonical) {
			return new ArtifactIdentity(identity(canonical));
		}
	}
}
