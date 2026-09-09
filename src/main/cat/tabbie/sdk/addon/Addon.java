package cat.tabbie.sdk.addon;

import java.net.URI;
import java.util.Set;

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

	public record Plugin() implements Addon {
	}

	public record Mod() implements Addon {
	}

	public record ModPack() implements Addon {
	}

	public record ResourcePack() implements Addon {
	}

	public record DataPack() implements Addon {
	}

	public record BehaviorPack() implements Addon {
	}
}
