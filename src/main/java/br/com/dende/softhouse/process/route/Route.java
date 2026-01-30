package br.com.dende.softhouse.process.route;

import java.lang.reflect.Method;
import java.util.List;

public record Route(
        HTTPMethod method,
        String path,
        Object controller,
        Method handlerMethod,
        List<String> pathVariables
        ) { }
