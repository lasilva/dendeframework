package br.com.dende.softhouse.annotations.request;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@RequestMapping(method = "POST")
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PostMapping {
    String path() default "";
}
