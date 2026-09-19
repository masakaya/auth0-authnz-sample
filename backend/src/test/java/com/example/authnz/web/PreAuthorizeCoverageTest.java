package com.example.authnz.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The security filter chain permits every request (see {@link
 * com.example.authnz.security.SecurityConfig}); all access control lives in
 * {@code @PreAuthorize} on controller methods. This test guards against a handler method
 * silently ending up unprotected because someone forgot the annotation.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PreAuthorizeCoverageTest {

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private static final String APPLICATION_BASE_PACKAGE = "com.example.authnz";

    @Test
    void everyApplicationHandlerDeclaresPreAuthorize() {
        Map<?, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();

        // Framework-provided controllers (e.g. Spring Boot's BasicErrorController for /error)
        // are out of scope: this test only guards handlers this application owns.
        List<HandlerMethod> applicationHandlers = handlerMethods.values().stream()
                .filter(handlerMethod -> handlerMethod
                        .getBeanType()
                        .getPackageName()
                        .startsWith(APPLICATION_BASE_PACKAGE))
                .toList();

        List<String> unprotected = applicationHandlers.stream()
                .filter(handlerMethod ->
                        !AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), PreAuthorize.class))
                .map(HandlerMethod::toString)
                .toList();

        assertThat(unprotected).as("handler methods missing @PreAuthorize").isEmpty();
        assertThat(applicationHandlers).isNotEmpty();
    }
}
