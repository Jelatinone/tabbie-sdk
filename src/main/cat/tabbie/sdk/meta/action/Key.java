package cat.tabbie.sdk.meta.action;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the element of an action whose value keys its contributors, such as
 * the file an activation edits. Contributors keyed by different values never
 * meet, so each value has a winner of its own. An action marks one element at
 * most, and one marking none keys its contributors by nothing. A class is
 * keyed by its binary name, and any other value as it prints.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Key {
}
