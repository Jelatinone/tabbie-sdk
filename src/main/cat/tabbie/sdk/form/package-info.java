/**
 * The forms a file's content is read and written in, other than bytes: plain
 * text in a charset, or JSON. A {@link Form} reads content as text a
 * configuration edits, and writes that text back into the file as it was. A
 * form is plain data: a configuration records the form it edits its file in,
 * so history recorded once replays anywhere.
 */
package cat.tabbie.sdk.form;
