package com.smartmart.common.error;

/**
 * The one error body every REST endpoint returns (GUIDELINES.md section 6).
 *
 * @param status  HTTP status code
 * @param error   reason phrase for the status, such as "Bad Request"
 * @param message what went wrong, safe to show to the user
 * @param path    request path
 */
public record ApiError(int status, String error, String message, String path) {
}
