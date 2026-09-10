package cat.tabbie.sdk.addon;

import java.net.URI;
import java.util.Set;

import cat.tabbie.sdk.Identity.AddonIdentity;
import cat.tabbie.sdk.Identity.ProviderIdentity;
import cat.tabbie.sdk.Identity.ReleaseIdentity;
import cat.tabbie.sdk.minecraft.Distribution;
import cat.tabbie.sdk.minecraft.Edition;
import cat.tabbie.sdk.minecraft.Version;
import lombok.NonNull;

public sealed interface Addon {

	@NonNull
	AddonIdentity addonId();

	@NonNull
	String addonName();

	@NonNull
	ProviderIdentity providedBy();

	@NonNull
	ReleaseIdentity releasedBy();

	@NonNull
	Set<AddonIdentity> dependentWith();

	@NonNull
	Set<AddonIdentity> conflictWith();

	@NonNull
	Set<Edition> editionOf();

	@NonNull
	Set<Version> versionOf();

	@NonNull
	Set<Distribution> distributionOf();

	// TODO: These records should be moved to addon.archetype package instead given
	// the number of them so they can have their own logic.

	public record Plugin() implements Addon {
		// TODO: Implement
	}

	public record Mod() implements Addon {
		// TODO: Implement
	}

	public record ModPack() implements Addon {
		// TODO: Implement
	}

	public record ResourcePack() implements Addon {
		// TODO: Implement
	}

	public record DataPack() implements Addon {
		// TODO: Implement
	}

	public record BehaviorPack() implements Addon {
		// TODO: Implement
	}
}
