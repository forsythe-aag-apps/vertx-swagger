package com.forsythe.vertxswagger.swagger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.forsythe.vertxswagger.RestVerticle;
import io.swagger.annotations.ApiOperation;
import io.swagger.models.Info;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.models.auth.SecuritySchemeDefinition;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.PathParameter;
import io.swagger.servlet.ReaderContext;
import io.swagger.servlet.extensions.ServletReaderExtension;
import io.swagger.util.Json;
import io.swagger.util.Yaml;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SwaggerGenerator {
    private static final Logger log = LoggerFactory.getLogger(SwaggerGenerator.class);

    public synchronized static void publishSwagger(Router router, SecuritySchemeDefinition securityDefinition,
                                                   String path, String title, String version, String baseUrl) {
        Swagger swagger = SwaggerGenerator.generate(router, title, version, baseUrl);
        swagger.addSecurityDefinition("auth", securityDefinition);
        router.get(path + ".json").handler(routingContext ->
                routingContext.response()
                        .putHeader("Content-Type", "application/json")
                        .end(Json.pretty(swagger)));
        router.get(path + ".yaml").handler(routingContext -> {
            try {
                routingContext.response()
                        .putHeader("Content-Type", "text/plain")
                        .end(Yaml.pretty().writeValueAsString(swagger));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        });
    }

    private static String getPath(Route route) {
        return route.getPath() != null ? route.getPath().replaceAll(":(?<p>\\w+)", "{${p}}"): null;
    }

    private static Swagger generate(Router router, String title, String version, String baseUrl) {
        Swagger swagger = new Swagger();
        swagger.setBasePath("/api/v1");
        Info info = new Info();
        info.setTitle(title);
        info.setVersion(version);
        swagger.setInfo(info);

        Map<String, Path> paths = extractAllPaths(router);
        extractOperationInfo(swagger, router, paths);
        swagger.setPaths(paths);
        return swagger;
    }

    private static Map<String,Path> extractAllPaths(Router router) {
        return router.getRoutes().stream().filter(x -> x.getPath() != null)
                .map(SwaggerGenerator::getPath).distinct().collect(Collectors.toMap(x -> x, x -> new Path()));
    }

    private static void extractOperationInfo(Swagger swagger, Router router, Map<String, Path> paths) {
        router.getRoutes().forEach(route -> {
            Path path = paths.get(getPath(route));
            if (path != null) {
                List<Operation> operations = extractOperations(route, path);
                operations.forEach(operation -> operation.setParameters(extractPathParams(route.getPath())));
            }
        });
        decorateOperationsFromAnnotationsOnHandlers(swagger, router, paths);
    }

    @SuppressWarnings("unchecked")
    private static void decorateOperationsFromAnnotationsOnHandlers(Swagger swagger, Router router, Map<String, Path> paths) {
        router.getRoutes().stream().filter(x -> x.getPath() != null).forEach(route -> {
            try {
                Field contextHandlers = route.getClass().getDeclaredField("contextHandler");
                contextHandlers.setAccessible(true);

                Field methods = route.getClass().getDeclaredField("methods");
                methods.setAccessible(true);

                List<Handler<RoutingContext>> handlers = Collections.singletonList((Handler<RoutingContext>) contextHandlers.get(route));
                handlers.forEach(handler -> {
                    try {
                        Class<?> delegate = handler.getClass().getDeclaredField("arg$1").getType();
                        Arrays.stream(delegate.getDeclaredMethods()).distinct().forEach(method -> {
                            ApiOperation annotation = method.getAnnotation(ApiOperation.class);
                            VertxPath vertxPath = method.getAnnotation(VertxPath.class);
                            if (annotation != null && vertxPath != null && route.getPath().endsWith(vertxPath.value())) {
                                Path path = paths.get(getPath(route));
                                Operation operation = path.getOperationMap().get(io.swagger.models.HttpMethod.valueOf(annotation.httpMethod()));
                                operation.setSummary(annotation.value());
                                operation.setDescription(annotation.notes());

                                ReaderContext context = new ReaderContext(swagger, RestVerticle.class, "", null, false, Collections.emptyList(),
                                        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

                                ServletReaderExtension extension = new ServletReaderExtension();
                                extension.setDeprecated(operation, method);
                                extension.applyConsumes(context, operation, method);
                                extension.applyProduces(context, operation, method);
                                extension.applyOperationId(operation, method);
                                extension.applySummary(operation, method);
                                extension.applyDescription(operation, method);
                                extension.applySchemes(context, operation, method);
                                extension.applySecurityRequirements(context, operation, method);
                                extension.applyTags(context, operation, method);
                                extension.applyResponses(context, operation, method);
                                extension.applyImplicitParameters(context, operation, method);
                                extension.applyExtensions( context, operation, method );

                            }
                        });
                    } catch (NoSuchFieldException e) {
                        log.warn(e.getMessage());
                    }
                });
            } catch (IllegalAccessException | NoSuchFieldException e) {
                log.warn(e.getMessage());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static List<Operation> extractOperations(Route route, Path pathItem) {
        try {
            Field methods = route.getClass().getDeclaredField("methods");
            methods.setAccessible(true);
            Set<HttpMethod> httpMethods = (Set<HttpMethod>) methods.get(route);
            return httpMethods.stream().map(httpMethod -> {
                Operation operation = new Operation();
                pathItem.set(httpMethod.name().toLowerCase(), operation);
                return operation;
            }).collect(Collectors.toList());

        } catch (NoSuchFieldException | IllegalAccessException e) {
            return Collections.emptyList();
        }
    }

    private static List<Parameter> extractPathParams(String fullPath) {
        String[] split = fullPath.split("\\/");
        return Arrays.stream(split).filter(x -> x.startsWith(":")).map(x -> {
            PathParameter param = new PathParameter();
            param.name(x.substring(1));
            return param;
        }).collect(Collectors.toList());
    }
}
