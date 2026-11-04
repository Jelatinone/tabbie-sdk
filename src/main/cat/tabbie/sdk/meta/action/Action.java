package cat.tabbie.sdk.meta.action;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an annotation as an action: something a module contributes that the
 * SDK chooses by target, placed on the method that provides it. Most actions
 * are verbs whose method carries a behaviour out, such as installing a
 * family's content; some are nouns whose method returns what they name, such
 * as the toolchain a server needs. Attributes on the method, or on a type
 * enclosing it, say which targets it applies to; a method with none applies to
 * every target.
 *
 * <p>
 * Every action has the same shape, so a new action is only an annotation
 * marked with this one, which names its {@link #value() signature}:
 * <ul>
 * <li>It declares {@code Class<?>[] subject() default {}}, the type a static
 * contribution acts on. An action whose contributions may act in different
 * places also declares {@code Closure value() default Closure.ROOT}, where
 * each acts; one that declares none always acts in the root. Annotations
 * inherit nothing, so each action declares these itself.</li>
 * <li>Each contribution has the action's signature: an instance method takes
 * the action's context, acting on the type declaring it; a static method takes
 * the type it acts on, then the context. Methods can then not be overloaded,
 * so they are named apart: {@code <action><Qualifier><Closure>}, followed for
 * versioned contributions by their bounds, such as {@code installRoot},
 * {@code installListedRoot} or {@code installRoot_1_20_5to26_3}.</li>
 * <li>Each method carrying it is read into a {@link Contribution}, and the
 * contributions of one action to one subject in a type are checked together
 * as a {@link Coverage}: no two apply to one target, and for each target,
 * every version between the lowest and highest bound they name is covered.
 * Versions outside those bounds are simply not supported.</li>
 * </ul>
 *
 * <p>
 * Actions and their attributes are kept at run time: the processors check
 * them while a module compiles, and the registry reads the same annotations
 * when it loads the module, calling each contribution through its action's
 * signature.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Action {

  /**
   * Returns the action's signature: a functional interface, nested in the
   * action, whose one method takes the subject the action acts on, then the
   * action's context, and returns and throws what every contribution may.
   *
   * @return the signature every contribution has
   */
  Class<?> value();
}
