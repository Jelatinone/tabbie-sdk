package cat.tabbie.sdk.addon;

import cat.tabbie.sdk.Identity.AddonIdentity;
import cat.tabbie.sdk.Identity.ProviderIdentity;
import cat.tabbie.sdk.api.Criteria;
import cat.tabbie.sdk.api.Queryable;
import lombok.NonNull;

public interface Provider<Criterion extends Criteria<AddonIdentity>> extends Queryable<Criterion, Addon> {

	@NonNull
	ProviderIdentity providerId();

	@NonNull
	String providerName();
}
