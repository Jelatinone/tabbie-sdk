package cat.tabbie.sdk.meta.attribute;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names the type a contribution acts on, when it is not the type declaring
 * it: a distribution's place for namespaces is
 * {@code @Place @Subject(Namespace.class)}. Only a contribution to an action
 * acting on types, such as a place or a base, names one, since nothing else
 * says what it acts on. An instance method acts on the type declaring it, and
 * a static contribution to an action acting on instances takes what it acts
 * on as its first parameter, whose type is its subject.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subject {

  /**
   * Returns the type the contribution acts on.
   *
   * @return the type it acts on
   */
  Class<?> value();
}
