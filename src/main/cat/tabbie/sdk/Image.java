package cat.tabbie.sdk;

public sealed interface Image<State extends Image.Alteration> {

	State before();

	State after();

	sealed interface Alteration {
	}

	sealed interface File extends Alteration {

		record Absent() implements File {
		}

		record Present() implements File {
		}
	}

	sealed interface Text extends Alteration {

		record Absent() implements Text {
		}

		record Present() implements Text {
		}
	}
}

record Place() implements Image<Image.File> {

	@Override
	public File before() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'before'");
	}

	@Override
	public File after() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'after'");
	}
}

record Configure() implements Image<Image.Text> {

	@Override
	public Text before() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'before'");
	}

	@Override
	public Text after() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'after'");
	}
}
