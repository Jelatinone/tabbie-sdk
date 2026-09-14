package cat.tabbie.sdk.addon.artifact;

import java.util.Set;

import cat.tabbie.sdk.Identity;
import cat.tabbie.sdk.Image;
import lombok.NonNull;

public record Mod() implements Artifact {

  @Override
  public @NonNull Identity<Artifact> artifactId() {
    throw new UnsupportedOperationException("Unimplemented method 'artifactId'");
  }

  @Override
  public @NonNull String artifactName() {
    throw new UnsupportedOperationException("Unimplemented method 'artifactName'");
  }

  @Override
  public long artifactSize() {
    throw new UnsupportedOperationException("Unimplemented method 'artifactSize'");
  }

  @Override
  public @NonNull Set<Identity<Artifact>> depends() {
    throw new UnsupportedOperationException("Unimplemented method 'artifactDependsOn'");
  }

  @Override
  public @NonNull Set<Identity<Artifact>> conflicts() {
    throw new UnsupportedOperationException("Unimplemented method 'artifactConflictsOn'");
  }

  @Override
  public @NonNull Set<Image<?>> images() {
    throw new UnsupportedOperationException("Unimplemented method 'artifactImages'");
  }
}
