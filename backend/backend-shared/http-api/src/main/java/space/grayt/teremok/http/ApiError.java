package space.grayt.teremok.http;

/** Stable public error contract; never includes exception names, SQL or stack traces. */
public record ApiError(
  int status,
  String code,
  String message,
  String correlationId
) {}
