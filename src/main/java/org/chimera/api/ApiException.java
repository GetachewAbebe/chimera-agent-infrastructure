package org.chimera.api;

public final class ApiException extends RuntimeException {
  private final int statusCode;
  private final ApiError apiError;

  public ApiException(int statusCode, String error, String detail) {
    super(detail);
    this.statusCode = statusCode;
    this.apiError = new ApiError(error, detail);
  }

  public int statusCode() {
    return statusCode;
  }

  public ApiError apiError() {
    return apiError;
  }
}
