package cat.tabbie.sdk.addon;

import java.time.Instant;
import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.minecraft.Label;

import lombok.NonNull;

/**
 * 
 * <h1>Addon</h1>
 * 
 * <p>
 * </p>
 * 
 */
public interface Addon {

	/**
	 * 
	 * @return
	 */
	Identity<Provider<?>> providerId();

	/**
	 * 
	 * @return
	 */
	@NonNull
	Identity<Addon> addonId();

	/**
	 * 
	 * @return
	 */
	@NonNull
	String addonName();

	/**
	 * 
	 * @return
	 */
	Set<Make> make();

	record Make(
			@NonNull Identity<Addon> addonId,

			@NonNull Identity<Make> makeId,

			@NonNull String makeName,
			@NonNull Instant makeDate,
			long makeNumber,

			@NonNull Set<Label> descriptors) {
	}

}
