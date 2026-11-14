/**
 * Shapes read in a format. A {@link Form} is one shape read in its format,
 * made by a {@link Form.Factory} through
 * {@link cat.tabbie.sdk.tree.Shape#as(Form.Factory) Shape#as}, and refusing
 * with a {@link cat.tabbie.sdk.tree.Reason.Content Content} reason when the
 * shape is not in it. The SDK ships one form, {@link Text}, the line view a
 * revision's diff reads through; every other format, JSON and archives
 * included, lives in {@code extensions}.
 */
package cat.tabbie.sdk.form;
