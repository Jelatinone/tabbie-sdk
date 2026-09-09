package cat.tabbie.sdk.addon;

import java.net.URL;

import lombok.NonNull;

public interface Provider<Criterion> extends Queryable<Criterion<AddonIdentity>, Addon> {

	@NonNull
	ProviderIdentity providerId();

	@NonNull
	String providerName();
}
