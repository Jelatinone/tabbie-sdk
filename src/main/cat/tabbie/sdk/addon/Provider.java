package cat.tabbie.sdk.addon;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.api.Criteria;
import cat.tabbie.sdk.api.Queryable;
import lombok.NonNull;

/**
 *
 * <h2>Provider</h2>
 *
 * <p>
 * Catalog discovery capability, independent of transport or storage. Providers
 * may describe remote, local, cached, or generated content. Implementations
 * return
 * validated immutable addon declarations and distinguish failed discovery from
 * absent results.
 * </p>
 *
 * @param <Criterion> provider-specific addon selection criteria
 */
public interface Provider<Criterion extends Criteria<Identity<Addon>>> extends Queryable<Criterion, Addon> {

	/**
	 * Stable provider identity, also used by its catalog addons
	 *
	 * @return provider identity
	 */
	@NonNull
	Identity<Provider<Criterion>> providerId();

	/**
	 * Human-readable canonical provider name
	 *
	 * @return provider name
	 */
	@NonNull
	String providerName();

}
