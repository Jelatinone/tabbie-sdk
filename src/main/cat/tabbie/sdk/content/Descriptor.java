package cat.tabbie.sdk.content;

import cat.tabbie.sdk.platform.Address;
import lombok.NonNull;

/**
 * A file content carries about itself, which is read but never edited, such
 * as the manifest a Bedrock pack declares its identity in. A descriptor is
 * found among the entries of the content's archive and read from there alone,
 * without unpacking anything, and knows where the content it describes lies
 * in that archive.
 */
public interface Descriptor {

  /**
   * Returns the folder the descriptor's file lies in, which is where the
   * content it describes lies.
   *
   * @return folder of the described content, the root address for content not
   *         wrapped in a folder of its own
   */
  @NonNull
  Address root();
}
