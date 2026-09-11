package cat.tabbie.sdk.addon;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.api.Criteria;
import cat.tabbie.sdk.api.Queryable;
import lombok.NonNull;

/**
 * 
 * <h1>Provider</h1>
 * 
 * @param <Criterion>
 */
public interface Provider<Criterion extends Criteria<Identity<Addon>>> extends Queryable<Criterion, Addon> {

	/**
	 * 
	 * @return
	 */
	@NonNull
	Identity<Provider<Criterion>> providerId();

	/**
	 * 
	 * @return
	 */
	@NonNull
	String providerName();

}