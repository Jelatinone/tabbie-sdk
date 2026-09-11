package cat.tabbie.sdk.api;

import java.util.Set;

import cat.tabbie.sdk.Image;

public interface Materializable<Material> {

	// TODO: Interface needs work, we can return a set of Images that are needed
	// to materialize an action. The only issue here being that we don't have
	// anything like #materialize for actually downloading something over the
	// network for example. The type argument is also ambiguous and needs binding.
	Set<Image<?>> album();
}
