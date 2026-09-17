package cat.tabbie.sdk.addon.artifact;

import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.album.Image;
import cat.tabbie.sdk.album.Store;
import cat.tabbie.sdk.minecraft.Label;

public record Mod(
		Identity<Artifact> artifactId,
		String artifactName,
		long artifactSize,

		Store store,

		Set<Label> labels,

		Set<Identity<Artifact>> depends,
		Set<Identity<Artifact>> conflicts,

		Set<Image<?>> images) implements Artifact {
}
