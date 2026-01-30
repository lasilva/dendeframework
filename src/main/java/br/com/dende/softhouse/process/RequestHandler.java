package br.com.dende.softhouse.process;

import br.com.dende.softhouse.annotations.request.PathVariable;
import br.com.dende.softhouse.annotations.request.RequestBody;
import br.com.dende.softhouse.process.route.HTTPMethod;
import br.com.dende.softhouse.process.route.ResponseEntity;
import br.com.dende.softhouse.process.route.Route;
import br.com.dende.softhouse.utils.JsonMapper;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class RequestHandler {

    private final Map<String, Route> routes;

    public RequestHandler(final Map<String, Route> routes) {
        this.routes = routes;
    }

    public void handle(HttpExchange httpExchange) throws IOException {

        final String path = httpExchange.getRequestURI().getPath();
        final HTTPMethod method = HTTPMethod.valueOf(httpExchange.getRequestMethod());

        Route route = routes
                .values()
                .stream()
                .filter(r -> r.method() == method)
                .filter(r -> {
                    String routePath = r.path();
                    String regex = routePath.replaceAll("\\{[^/]+}", "([^/]+)");
                    Pattern pattern = Pattern.compile("^" + regex + "$");
                    return pattern.matcher(path).matches();
                }).findFirst()
                .orElse(null);

        if (route == null) {
            httpExchange.sendResponseHeaders(404, -1);
            return;
        }

        try {

            Map<String, String> vars = extractPathVariables(route, path);
            Object[] args = resolveMethodArguments(route.handlerMethod(), vars, httpExchange);

            Object result = route.handlerMethod().invoke(route.controller(), args);
            byte[] body;

            if (result instanceof ResponseEntity<?> responseEntity) {

                responseEntity.headers()
                        .forEach((k, v) ->
                                httpExchange.getResponseHeaders().add(k, v)
                        );

                body = responseEntity.body() == null
                        ? new byte[0]
                        : JsonMapper.toJson(responseEntity.body());

                httpExchange.sendResponseHeaders(responseEntity.status(), body.length);

            } else {
                body = result.toString().getBytes();
                httpExchange.sendResponseHeaders(200, body.length);
            }

            try (OutputStream os = httpExchange.getResponseBody()) {
                os.write(body);
            }

        } catch (Exception e) {
            httpExchange.sendResponseHeaders(500, -1);
            e.printStackTrace();
        }
    }

    private Object[] resolveMethodArguments(
            Method method,
            Map<String, String> pathVariables,
            HttpExchange exchange
    ) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];

            if(parameter.isAnnotationPresent(RequestBody.class)) {
                args[i] = JsonMapper.fromJson(
                        exchange.getRequestBody(),
                        parameter.getType()
                );
            } else if (parameter.isAnnotationPresent(PathVariable.class)) {
                PathVariable pv = parameter.getAnnotation(PathVariable.class);

                String name = pv.parameter().isBlank()
                        ? parameter.getName()
                        : pv.parameter();

                String value = pathVariables.get(name);

                args[i] = convert(value, parameter.getType());
            } else {
                args[i] = null;
            }
        }

        return args;
    }

    private Map<String, String> extractPathVariables(
            Route route,
            String requestPath
    ) {
        Pattern pattern = buildPathPattern(route.path());
        Matcher matcher = pattern.matcher(requestPath);

        if (!matcher.matches()) {
            return Map.of();
        }

        Map<String, String> values = new HashMap<>();
        List<String> names = route.pathVariables();

        for (int i = 0; i < names.size(); i++) {
            values.put(names.get(i), matcher.group(i + 1));
        }

        return values;
    }

    private Pattern buildPathPattern(String routePath) {
        String regex = routePath.replaceAll("\\{[^/]+}", "([^/]+)");
        return Pattern.compile("^" + regex + "$");
    }

    private Object convert(String value, Class<?> type) {
        if (type.equals(String.class)) return value;
        if (type.equals(Integer.class) || type.equals(int.class)) return Integer.valueOf(value);
        if (type.equals(Long.class) || type.equals(long.class)) return Long.valueOf(value);
        if (type.equals(Boolean.class) || type.equals(boolean.class)) return Boolean.valueOf(value);

        throw new IllegalArgumentException("Unsupported parameter type: " + type);
    }


}
