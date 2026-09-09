package cat.tabbie.sdk.api;

public interface Acknowledgement {

	void success();

	void retry();

	void error();
}
