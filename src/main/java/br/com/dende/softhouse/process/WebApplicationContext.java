package br.com.dende.softhouse.process;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class WebApplicationContext {

    private final Map<Class<?>, Object> beans;

    public WebApplicationContext() {
        this.beans = new HashMap<>();
    }

    public void registerBean(Class<?> clazz, Object instance) {
        beans.put(clazz, instance);
    }

    public <T> T getBean(Class<T> clazz) {
        return clazz.cast(beans.get(clazz));
    }

    public Collection<Object> getAllBeans() {
        return beans.values();
    }

    public Map<Class<?>, Object> getBeansWithAnnotation(final Class<? extends Annotation> annotation) {
        return this.beans
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().isAnnotationPresent(annotation))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

}
