package cat.tabbie.sdk;

import javax.print.attribute.standard.Destination;

public sealed interface Image<State> {

	// TODO: This whole interface still needs work, an Image is meant to be a
	// representation of the changes that occurred; and an operation should be able
	// to run both forwards and backward based on the set<images> it has. We bound
	// the interface by records so we aren't just arbitrarily running code. This
	// will also help track changes in the history graph, we'll be able to show the
	// exact changes. We should take into reference how Git works for the entire
	// Task system. The type argument is also ambiguous and needs binding. Could

	State before();

	State after();

	record Placement(
			Destination destination) implements Image<File> {
	}

	record Configure(
			Configuration change) implements Image<Void> {
	}
}
