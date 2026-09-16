package cat.tabbie.sdk.addon.artifact;

import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.minecraft.Label;
import lombok.NonNull;

public record Mod(
		Identity<Artifact> artifactId,
		String artifactName,
		long artifactSize,

		Store store,

		Set<Label> labels,

		Set<Identity<Artifact>> depends,
		Set<Identity<Artifact>> conflicts,

		Set<Image<?>> images) implements Artifact {

	@SuppressWarnings("unlikely-arg-type")
	public Mod {
		if (labels.stream().anyMatch(label -> !label.distribution().capabilities().contains(this))) {
			throw new IllegalArgumentException("Artifact labels must all be supported by declared labels");
		}
	}

	@SuppressWarnings("unlikely-arg-type")
	@Override
	public @NonNull boolean allow(@NonNull Label target) {
		return target.distribution().capabilities().contains(this);
	}
}
