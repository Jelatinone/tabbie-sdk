/**
 * Provider-agnostic read requests and progress reporting. A {@link Queryable}
 * answers typed {@link Query} requests built from {@link Criteria}, and
 * distinguishes an absent item from a failed request. {@link Observer}
 * receives progress and outcome callbacks from an observed operation, and
 * {@link Response} names HTTP status codes for providers that map remote
 * failures.
 */
package cat.tabbie.sdk.api;
