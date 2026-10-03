package cat.tabbie.sdk.platform;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.NonNull;

/**
 * A language runtime an executable needs, provisioned and located by the
 * executor rather than by the installation. This is separate from the runtime
 * running Tabbie itself: a Minecraft server may need an older or newer Java
 * than Tabbie does.
 */
public sealed interface Toolchain permits Toolchain.Java {

	/**
	 * Returns stable text naming the requirement, such as {@code java:21+} or
	 * {@code java:8-8}, for persistence and display.
	 *
	 * @return canonical requirement text
	 */
	@NonNull
	String canonical();

	/**
	 * Parses text produced by {@link #canonical()}.
	 *
	 * @param canonical canonical requirement text
	 * @return toolchain requirement
	 * @throws IllegalArgumentException when the text is not canonical
	 */
	static Toolchain parse(@NonNull String canonical) {
		Matcher matcher = Pattern.compile("java:([1-9][0-9]*)(?:\\+|-([1-9][0-9]*))").matcher(canonical);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Not canonical toolchain text: " + canonical);
		}
		int minimum = Integer.parseInt(matcher.group(1));
		return matcher.group(2) == null
				? Java.atLeast(minimum)
				: new Java(minimum, OptionalInt.of(Integer.parseInt(matcher.group(2))));
	}

	/**
	 * A Java runtime whose feature release lies within an inclusive range.
	 *
	 * @param minimum lowest accepted feature release, such as {@code 21}
	 * @param maximum highest accepted feature release, or empty for no upper
	 *                bound
	 */
	record Java(int minimum, @NonNull OptionalInt maximum) implements Toolchain {

		/**
		 * Checks that the range is nonempty and starts at a real feature release.
		 *
		 * @throws IllegalArgumentException when the minimum is below 1 or the
		 *                                  maximum is below the minimum
		 */
		public Java {
			if (minimum < 1 || maximum.isPresent() && maximum.getAsInt() < minimum) {
				throw new IllegalArgumentException("A Java toolchain needs a positive, nonempty feature range.");
			}
		}

		/**
		 * Requires a feature release or any later one.
		 *
		 * @param minimum lowest accepted feature release
		 * @return open-ended requirement
		 */
		public static Java atLeast(int minimum) {
			return new Java(minimum, OptionalInt.empty());
		}

		/**
		 * Requires exactly one feature release.
		 *
		 * @param feature accepted feature release
		 * @return exact requirement
		 */
		public static Java exactly(int feature) {
			return new Java(feature, OptionalInt.of(feature));
		}

		/**
		 * Checks whether a runtime's feature release satisfies this requirement.
		 *
		 * @param feature runtime feature release, such as {@code 25}
		 * @return whether the release lies within the range
		 */
		public boolean accepts(int feature) {
			return feature >= minimum && (maximum.isEmpty() || feature <= maximum.getAsInt());
		}

		@Override
		public String canonical() {
			return maximum.isEmpty()
					? "java:" + minimum + "+"
					: "java:" + minimum + "-" + maximum.getAsInt();
		}
	}
}