package cat.tabbie.sdk.minecraft;

import java.util.Arrays;
import java.util.Locale;

import cat.tabbie.sdk.addon.artifact.Artifact;
import cat.tabbie.sdk.minecraft.distribution.Distribution;
import lombok.NonNull;

/**
 * One structurally valid target declaration. This checks edition and runtime
 * environment, not whether an adapter has discovered this exact game release.
 *
 * @param version      discovered game version
 * @param distribution runtime distribution
 * @param environment  physical runtime environment
 */
public record Label(@NonNull Version version, @NonNull Distribution distribution, @NonNull Environment environment) {

	/**
	 * Checks edition and environment support without resolving a runtime release.
	 */
	public Label {
		if (!distribution.applicable(version)) {
			throw new IllegalArgumentException("Distribution does not support this version!");
		}
		if (!distribution.environments().contains(environment)) {
			throw new IllegalArgumentException("Distribution does not support this environment");
		}
	}

	/**
	 * Returns stable text naming the target, such as
	 * {@code java:fabric/1.21.1/server}, for persistence. The distribution
	 * identifier implies the edition. Labels that {@link #match(Label)} share
	 * canonical text.
	 *
	 * @return canonical label text
	 */
	public String canonical() {
		return distribution.id() + "/" + version.id() + "/" + environment.name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Parses text produced by {@link #canonical()}. Release metadata is not part
	 * of the canonical text, so the version has an unknown release type and date.
	 *
	 * @param canonical canonical label text
	 * @return label
	 * @throws IllegalArgumentException when the text is not canonical, names an
	 *                                  unknown distribution, or describes an
	 *                                  unsupported target
	 */
	public static Label parse(@NonNull String canonical) {
		String[] parts = canonical.split("/", -1);
		if (parts.length != 3) {
			throw new IllegalArgumentException("Not canonical label text: " + canonical);
		}
		Distribution distribution = Distribution.of(parts[0])
				.orElseThrow(() -> new IllegalArgumentException("Unknown distribution: " + parts[0]));
		String edition = parts[0].substring(0, parts[0].indexOf(':'));
		Environment environment = Arrays.stream(Environment.values())
				.filter(candidate -> candidate.name().toLowerCase(Locale.ROOT).equals(parts[2]))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown environment: " + parts[2]));
		Label label = new Label(Version.parse(edition + ":" + parts[1]), distribution, environment);
		if (!label.canonical().equals(canonical)) {
			throw new IllegalArgumentException("Not canonical label text: " + canonical);
		}
		return label;
	}

	/**
	 * Compares target identity independently of release date and classification.
	 *
	 * @param other target declaration to compare
	 * @return whether edition, game version, distribution, and environment agree
	 */
	public boolean match(@NonNull Label other) {
		return version.match(other.version)
				&& distribution.equals(other.distribution)
				&& environment == other.environment;
	}

	/**
	 * Assesses type support followed by explicit target declarations. Unlisted
	 * targets remain unknown; no compatibility between runtime forks is inferred.
	 *
	 * @param artifact artifact to assess against this target
	 * @return structural rejection, declared support, or unknown support
	 */
	public Compatibility compatibility(@NonNull Artifact artifact) {
		if (!distribution.supports(artifact.getClass())) {
			return Compatibility.UNSUPPORTED;
		}
		return artifact.labels().stream().anyMatch(label -> label.match(this))
				? Compatibility.SUPPORTED
				: Compatibility.UNKNOWN;
	}
}
