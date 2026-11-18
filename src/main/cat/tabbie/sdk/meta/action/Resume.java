package cat.tabbie.sdk.meta.action;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import lombok.NonNull;

/**
 * Resumes a running Cat's saving after a {@link Hold hold}: the method
 * describes the console steps, for the targets its
 * {@link cat.tabbie.sdk.meta.attribute.Applies} declarations name, such as
 * Java's {@code save-on} or Bedrock's {@code save resume}. Like a
 * {@link Start start}, it acts on a distribution as a type and is given the
 * start's {@link Start.Context context}. At most one applies to a target, and a
 * distribution declaring a hold declares a resume.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Action(value = Resume.Signature.class, cardinality = Cardinality.ONE)
public @interface Resume {

  /**
   * The signature of every resume, as the registry calls it.
   */
  @FunctionalInterface
  interface Signature {

    /**
     * Describes how a running Cat's saving resumes.
     *
     * @param context the Cat's root, its target, its release and its settings
     * @return the console steps, in order
     * @throws IOException when the resume depends on a file that cannot be read
     */
    @NonNull
    List<Hold.Step> resume(@NonNull Start.Context context) throws IOException;
  }
}
