package br.com.dende.softhouse.process.route;

public enum HTTPMethod {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    PATCH("PATCH"),
    DELETE("DELETE");

    private final String method;

    HTTPMethod(final String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

}
