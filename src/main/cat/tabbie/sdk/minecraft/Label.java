package cat.tabbie.sdk.minecraft;

import lombok.NonNull;

public record Label(

		@NonNull Version version,
		@NonNull Distribution distribution,
		@NonNull Environment environment) {
}
