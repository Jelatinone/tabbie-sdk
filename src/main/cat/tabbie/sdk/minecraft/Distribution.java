package cat.tabbie.sdk.minecraft;

import java.util.Set;

import cat.tabbie.sdk.addon.artifact.Artifact;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 *
 * <h1>Distribution</h1>
 *
 * <p>
 * Represents a Minecraft software distribution capable of providing a
 * runnable game or server environment.
 * </p>
 *
 */
public sealed interface Distribution permits Distribution.Java, Distribution.Bedrock {

	/**
	 * Stable namespaced distribution identifier
	 *
	 * @return identifier
	 */
	String id();

	/**
	 * Structurally supported physical environments
	 *
	 * @return supported environments
	 */
	Set<Environment> environments();

	/**
	 * Structurally supported artifact capabilities
	 *
	 * @return supported artifacts
	 */
	Set<Class<? extends Artifact>> capabilities();

	/**
	 * Tests edition membership without discovering a runtime release.
	 * 
	 * @param version discovered game version
	 * @return whether the version belongs to the supported edition
	 */
	boolean applicable(@NonNull Version version);

	sealed interface Java extends Distribution {

		@AllArgsConstructor
		@FieldDefaults(makeFinal = true)
		enum Of implements Java {

			JAVA_NATIVE(new Java.Native()),

			FABRIC(new Java.Fabric()),
			QUILT(new Java.Quilt()),
			FORGE(new Java.Forge()),
			NEO_FORGE(new Java.NeoForge()),

			CRAFT_BUKKIT(new Java.CraftBukkit()),
			SPIGOT(new Java.Spigot()),
			PAPER(new Java.Paper()),
			PURPUR(new Java.Purpur()),
			FOLIA(new Java.Folia());

			@NonNull
			Distribution distribution;

			@Override
			public String id() {
				return distribution.id();
			}

			@Override
			public Set<Environment> environments() {
				return distribution.environments();
			}

			@Override
			public Set<Class<? extends Artifact>> capabilities() {
				return distribution.capabilities();
			}

			@Override
			public boolean applicable(@NonNull Version version) {
				return distribution.applicable(version);
			}
		}

		@Override
		default Set<Environment> environments() {
			return Set.of(Environment.CLIENT, Environment.SERVER);
		}

		@Override
		default Set<Class<? extends Artifact>> capabilities() {
			// Default artifact types here soon:)
			return Set.of();
		}

		@Override
		default boolean applicable(@NonNull Version version) {
			return Version.Java.applicable(version);
		}

		record Native() implements Java {

			@Override
			public String id() {
				return "java:native";
			}
		}

		record Fabric() implements Launcher {

			@Override
			public String id() {
				return "java:fabric";
			}
		}

		record Quilt() implements Launcher {

			@Override
			public String id() {
				return "java:quilt";
			}
		}

		record Forge() implements Launcher {
			@Override
			public String id() {
				return "java:forge";
			}
		}

		record NeoForge() implements Launcher {
			@Override
			public String id() {
				return "java:neoforge";
			}
		}

		record CraftBukkit() implements Plugin {
			@Override
			public String id() {
				return "java:craftbukkit";
			}
		}

		record Spigot() implements Plugin {

			@Override
			public String id() {
				return "java:spigot";
			}
		}

		record Paper() implements Plugin {

			@Override
			public String id() {
				return "java:paper";
			}
		}

		record Purpur() implements Plugin {

			@Override
			public String id() {
				return "java:purpur";
			}
		}

		record Folia() implements Plugin {

			@Override
			public String id() {
				return "java:folia";
			}
		}

		sealed interface Launcher extends Java {

			@Override
			default Set<Class<? extends Artifact>> capabilities() {
				// Default artifact types here soon:)
				return Set.of();
			}
		}

		sealed interface Plugin extends Java {

			@Override
			default Set<Environment> environments() {
				return Set.of(Environment.SERVER);
			}

			@Override
			default Set<Class<? extends Artifact>> capabilities() {
				// Default artifact types here soon:)
				return Set.of();
			}
		}
	}

	sealed interface Bedrock extends Distribution {

		@Override
		default Set<Environment> environments() {
			return Set.of(Environment.CLIENT, Environment.SERVER);
		}

		@Override
		default Set<Class<? extends Artifact>> capabilities() {
			// Default artifact types here soon:)
			return Set.of();
		}

		@Override
		default boolean applicable(@NonNull Version version) {
			return Version.Bedrock.applicable(version);
		}

		@AllArgsConstructor
		@FieldDefaults(makeFinal = true)
		enum Of implements Bedrock {

			BEDROCK_NATIVE(new Bedrock.Native()),

			POCKET_MINE(new Bedrock.PocketMine()),
			NUKKIT(new Bedrock.Nukkit()),
			POWER_NUKKITX(new Bedrock.PowerNukkitX());

			@NonNull
			Distribution distribution;

			@Override
			public String id() {
				return distribution.id();
			}

			@Override
			public Set<Environment> environments() {
				return distribution.environments();
			}

			@Override
			public Set<Class<? extends Artifact>> capabilities() {
				return distribution.capabilities();
			}

			@Override
			public boolean applicable(@NonNull Version version) {
				return distribution.applicable(version);
			}
		}

		record Native() implements Distribution.Bedrock {

			@Override
			public String id() {
				return "bedrock:native";
			}
		}

		record PocketMine() implements Plugin {

			@Override
			public String id() {
				return "bedrock:pocketmine";
			}
		}

		record Nukkit() implements Plugin {

			@Override
			public String id() {
				return "bedrock:nukkit";
			}
		}

		record PowerNukkitX() implements Plugin {

			@Override
			public String id() {
				return "bedrock:powernukkitx";
			}
		}

		sealed interface Plugin extends Distribution.Bedrock {

			@Override
			default Set<Environment> environments() {
				return Set.of(Environment.SERVER);
			}

			@Override
			default Set<Class<? extends Artifact>> capabilities() {
				return Set.of();
			}
		}
	}
}
