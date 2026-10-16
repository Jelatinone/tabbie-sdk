/**
 * Descriptions of change and the deferred work that produces them. An
 * {@link Image} describes one change to a file's existence, a text file's
 * content (as {@link Chunk}s), or a package claim. Images compose and reduce,
 * and each has a preimage that exchanges its before and after states, all
 * without touching a filesystem. An {@link Intermediate} is
 * unevaluated work that performs its effects only when collapsed with an
 * execution context.
 *
 * <p>
 * Scrutinizing an image checks it against its target as it is now, reading
 * files and running package managers' read-only checks without changing
 * anything. Applying it belongs to the consumer.
 */
package cat.tabbie.sdk.album.revision;
