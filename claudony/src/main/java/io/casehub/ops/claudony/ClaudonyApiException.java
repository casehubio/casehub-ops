package io.casehub.ops.claudony;

public class ClaudonyApiException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public ClaudonyApiException(final int statusCode, final String responseBody) {
        super("Claudony API returned " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int statusCode() { return statusCode; }

    public String responseBody() { return responseBody; }
}
