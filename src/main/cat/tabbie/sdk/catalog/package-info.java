/**
 * Catalogs, and what they publish. A {@link Provider} finds projects and their
 * {@link Release}s as a {@link Queryable}, answering typed {@link Query}
 * requests built from {@link Criteria}, and mints {@link Coordinate}s that let
 * a later process ask the same provider for the same project, release, or
 * file. An {@link Addon} is a provider-owned project of content, and a
 * {@link Product} one publishing releases of a distribution; each of their
 * builds is a release. An {@link Identity} is the stable UUID reference naming
 * providers and catalog items independently of revisions and content hashes.
 *
 * <p>
 * Declarations validate only their own consistency; whether external
 * dependencies are available is resolved later. Transport, storage, and
 * caching belong to each provider.
 */
package cat.tabbie.sdk.catalog;
