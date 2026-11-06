/**
 * The annotation processors checking a module's {@code meta} annotations
 * while it compiles, and generating its registration. javac finds them on
 * the processor path, where a module puts the SDK; the SDK compiles itself
 * with them too. They run only in the compiler, so this package is not
 * exported, and nothing outside it sees a compiler type.
 *
 * <p>
 * {@link Processor} holds what every processor shares: the annotations it
 * reads, the compiler's utilities, reporting errors, and reading elements
 * into the same {@link cat.tabbie.sdk.meta.action.Contributor} and
 * {@link cat.tabbie.sdk.meta.attribute.Conditions} records the registry
 * reads at run time. Each subject's processor holds only its own rules:
 * {@link ActionProcessor} checks every action and its contributions, each
 * against its action's signature, and {@link RegistrationProcessor} what a
 * module registers, writing the registration listing it.
 */
package cat.tabbie.sdk.meta.processor;
