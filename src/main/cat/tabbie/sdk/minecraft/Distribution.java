package cat.tabbie.sdk.minecraft;

import java.util.Set;

import cat.tabbie.sdk.addon.artifact.Artifact;
import lombok.AccessLevel;
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
	 *
	 * @param version
	 * @return
	 */
	boolean applicable(@NonNull Version version);

	@AllArgsConstructor
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	public enum Java implements Distribution {

		// TODO: Enum Values Need Completion
		NATIVE("");

		@NonNull
		String id;

		@NonNull
		Set<Class<? extends Artifact>> capabilities;
		@NonNull
		Set<Environment> environments;

		@SafeVarargs
		Java(String id, Class<? extends Artifact>... capabilities) {
			this(id, Set.of(capabilities), Set.of(Environment.SERVER, Environment.CLIENT));
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public Set<Class<? extends Artifact>> capabilities() {
			return capabilities;
		}

		@Override
		public Set<Environment> environments() {
			return environments;
		}

		@Override
		public boolean applicable(@NonNull Version version) {
			return Version.Java.applicable(version);
		}
	}

	@AllArgsConstructor
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	public enum Bedrock implements Distribution {

		// TODO: Enum Values Need Completion
		NATIVE("");

		@NonNull
		String id;

		@NonNull
		Set<Class<? extends Artifact>> capabilities;
		@NonNull
		Set<Environment> environments;

		@SafeVarargs
		Bedrock(String id, Class<? extends Artifact>... capabilities) {
			this(id, Set.of(capabilities), Set.of(Environment.SERVER, Environment.CLIENT));
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public Set<Class<? extends Artifact>> capabilities() {
			return capabilities;
		}

		@Override
		public Set<Environment> environments() {
			return environments;
		}

		@Override
		public boolean applicable(@NonNull Version version) {
			return Version.Bedrock.applicable(version);
		}
	}
}
