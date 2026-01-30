package br.com.dende.softhouse.process;

import br.com.dende.softhouse.annotations.Component;

import br.com.dende.softhouse.annotations.Controller;
import br.com.dende.softhouse.annotations.request.*;
import br.com.dende.softhouse.process.route.HTTPMethod;
import br.com.dende.softhouse.process.route.Route;
import com.sun.net.httpserver.HttpServer;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Map.entry;

public class WebApplication {

    private static final Set<Class<? extends Annotation>> HTTP_METHOD_ANNOTATIONS = Set.of(
            GetMapping.class,
            PostMapping.class,
            PutMapping.class,
            PatchMapping.class,
            DeleteMapping.class,
            RequestMapping.class
    );
    private static final Set<Class<? extends Annotation>> CLASS_ANNOTATIONS = Set.of(
            Component.class,
            Controller.class,
            RequestMapping.class
    );

    private final Class<?> mainApplicationClass;
    private final WebApplicationContext webApplicationContext;
    private final RequestHandler requestHandler;

    public WebApplication(final Class<?> mainClass) {
        this.mainApplicationClass = mainClass;
        this.webApplicationContext = new WebApplicationContext();
        scanComponents();
        this.requestHandler = new RequestHandler(createRoutes());
    }

    public void run() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", requestHandler::handle);

        server.setExecutor(null); // executor padrão
        server.start();

        System.out.println("🚀 Server running at http://localhost:8080");
    }

    private void scanComponents() {
        String basePackage = mainApplicationClass.getPackageName();

        try (ScanResult scanResult = new ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .acceptPackages(basePackage)
                .scan()) {

            CLASS_ANNOTATIONS
                    .forEach( annotation -> {
                        scanResult
                            .getClassesWithAnnotation(annotation.getName())
                            .loadClasses()
                            .forEach(this::createAndRegister);
                    });

            HTTP_METHOD_ANNOTATIONS
                    .forEach(annotation -> {
                        scanResult
                                .getClassesWithMethodAnnotation(annotation.getName())
                                .loadClasses()
                                .forEach(this::createAndRegister);
                    });
        }

        webApplicationContext
                .getAllBeans()
                .stream()
                .map(Object::getClass)
                .flatMap(clazz -> Arrays.stream(clazz.getDeclaredMethods()))
                .filter(this::hasAnyHttpMethodAnnotation)
                .forEach(this::validateRouteMethod);
    }

    private void validateRouteMethod(Method method) {
        validateHttpAnnotations(method);
        validatePathVariables(method);
    }

    private void validateHttpAnnotations(Method method) {
        List<Class<? extends Annotation>> found =
                HTTP_METHOD_ANNOTATIONS.stream()
                        .filter(method::isAnnotationPresent)
                        .toList();

        if (found.size() > 1) {
            throw new IllegalStateException(
                    "Method " + method.getDeclaringClass().getName() + "#" + method.getName()
                            + " has conflicting HTTP annotations: "
                            + found.stream()
                            .map(Class::getSimpleName)
                            .collect(Collectors.joining(", "))
            );
        }
    }

    private void validatePathVariables(Method method) {
        Annotation mapping = extractHttpMethodAnnotation(method)
                .orElseThrow(() -> new IllegalStateException(
                        "Method without HTTP mapping: " + method
                ));

        String methodPath = extractPath(mapping);

        Set<String> pathVarsInPath = new HashSet<>(extractPathVariables(methodPath));
        Set<String> pathVarsInParams = extractPathVarsInParams(method, pathVarsInPath, methodPath);

        // path variable without a correspondent arg
        for (String pathVar : pathVarsInPath) {
            if (!pathVarsInParams.contains(pathVar)) {
                throw new IllegalStateException(
                        "Path variable '{" + pathVar + "}' has no matching @PathVariable " +
                                "parameter in method " + method
                );
            }
        }
    }

    private static @NotNull Set<String> extractPathVarsInParams(Method method, Set<String> pathVarsInPath, String methodPath) {
        Set<String> pathVarsInParams = extractVarInParams(method);

        // parameter without a correspondent path variable
        for (String param : pathVarsInParams) {
            if (!pathVarsInPath.contains(param)) {
                throw new IllegalStateException(
                        "@PathVariable '" + param +
                                "' not present in path '" + methodPath +
                                "' at " + method
                );
            }
        }
        return pathVarsInParams;
    }

    private static @NotNull Set<String> extractVarInParams(Method method) {
        Set<String> pathVarsInParams = new HashSet<>();

        for (Parameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(PathVariable.class)) {
                PathVariable pv = parameter.getAnnotation(PathVariable.class);

                String name = pv.parameter().isBlank()
                        ? parameter.getName()
                        : pv.parameter();

                if (!pathVarsInParams.add(name)) {
                    throw new IllegalStateException(
                            "Duplicate @PathVariable '" + name +
                                    "' in method " + method
                    );
                }
            }
        }
        return pathVarsInParams;
    }


    private void createAndRegister(Class<?> clazz) {
        try {
            if(!clazz.isAnnotation() && webApplicationContext.getBean(clazz) == null) {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                webApplicationContext.registerBean(clazz, instance);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error while creating an object of Class: " + clazz.getName(), e
            );
        }
    }

    private Map<String, Route> createRoutes() {
        final Map<String, Route> routes = new HashMap<>();
        webApplicationContext.getAllBeans().forEach(bean -> {
            Class<?> clazz = bean.getClass();
            String basePath = extractPath(clazz.getAnnotation(RequestMapping.class));

            Map<String, Route> beanRoutes =
                    Arrays.stream(clazz.getDeclaredMethods())
                            .filter(this::hasAnyHttpMethodAnnotation)
                            .flatMap(method ->
                                    extractHttpMethodAnnotation(method)
                                            .map(annotation -> createRouteEntry(
                                                    basePath, annotation, bean, method
                                            ))
                                            .stream()
                            )
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue
                            ));

            routes.putAll(beanRoutes);
        });
        return routes;
    }

    private Map.Entry<String, Route> createRouteEntry(
            String basePath,
            Annotation annotation,
            Object bean,
            Method method
    ) {
        String fullPath = basePath + extractPath(annotation);
        HTTPMethod httpMethod = extractMethod(annotation);

        return entry(
                fullPath,
                new Route(httpMethod, fullPath, bean, method, extractPathVariables(fullPath))
        );
    }

    private boolean hasAnyHttpMethodAnnotation(Method method) {
        return HTTP_METHOD_ANNOTATIONS.stream()
                .anyMatch(method::isAnnotationPresent);
    }

    private Optional<? extends Annotation> extractHttpMethodAnnotation(Method method) {
        return HTTP_METHOD_ANNOTATIONS.stream()
                .filter(method::isAnnotationPresent)
                .map(method::getAnnotation)
                .findFirst();
    }

    private String extractPath(Annotation annotation) {
        try {
            Method pathMethod = Objects.nonNull(annotation) ? annotation.annotationType().getMethod("path") : null;
            return Objects.nonNull(pathMethod) ? normalizePath((String) pathMethod.invoke(annotation)) : "";
        } catch (Exception e) {
            throw new RuntimeException("Annotation does not have path attribute", e);
        }
    }

    private HTTPMethod extractMethod(Annotation annotation) {

        if (annotation instanceof RequestMapping requestMapping) {
            return HTTPMethod.valueOf(requestMapping.method());
        }

        RequestMapping meta = annotation.annotationType().getAnnotation(RequestMapping.class);
        if (meta != null) {
            return HTTPMethod.valueOf(meta.method());
        }

        throw new IllegalStateException("Unknown HTTP mapping annotation");
    }

    private List<String> extractPathVariables(String path) {
        List<String> variables = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\{([^}]+)}").matcher(path);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }


    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String result = path.trim();
        if (!result.startsWith("/")) {
            result = "/" + result;
        }
        if (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public WebApplicationContext getContext() {
        return webApplicationContext;
    }
}
