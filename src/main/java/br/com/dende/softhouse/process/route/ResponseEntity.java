package br.com.dende.softhouse.process.route;

import java.util.Map;

public class ResponseEntity<T> {

    private final int status;
    private final T body;
    private final Map<String, String> headers;

    private ResponseEntity(int status, T body, Map<String, String> headers) {
        this.status = status;
        this.body = body;
        this.headers = headers;
    }

    public static <T> ResponseEntity<T> ok(T body) {
        return new ResponseEntity<>(200, body, Map.of());
    }

    public static <T> ResponseEntity<T> status(int status, T body) {
        return new ResponseEntity<>(status, body, Map.of());
    }

    public int status() {
        return status;
    }

    public T body() {
        return body;
    }

    public Map<String, String> headers() {
        return headers;
    }
}
