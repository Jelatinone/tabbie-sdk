package cat.tabbie.sdk.api;

public sealed interface Query<Criterion> {

	Criterion criteria();

	public record Exists<Criterion>(Criterion criteria) implements Query<Criterion> {
	}

	public record Count<Criterion>(Criterion criteria) implements Query<Criterion> {
	}

	public record Singular<Criterion>(Criterion criteria) implements Query<Criterion> {
	}

	public record Several<Criterion>(Criterion criteria, Integer limit) implements Query<Criterion> {

		public Several(Criterion criteria) {
			this(criteria, null);
		}

		public Several {
			if (limit < 1) {
				throw new IllegalArgumentException("limit must be positive");
			}
		}
	}
}
