package com.forsythe.vertxswagger;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import com.forsythe.vertxswagger.domain.User;
import com.forsythe.vertxswagger.swagger.SwaggerGenerator;
import com.forsythe.vertxswagger.swagger.VertxPath;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.In;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RestVerticle extends AbstractVerticle {
    private static final String API_USER_BY_ID = "/users/:id";
    private static final String API_USERS = "/users";

    private static List<User> users = new ArrayList<User>() {{
        add(new User("user1", "user1@test.com", "User", "Test 1"));
        add(new User("user2", "user2@test.com", "User", "Test 2"));
        add(new User("user3", "user3@test.com", "User", "Test 3"));
    }};

    @Override
    public void start() throws Exception {
        super.start();

        Router router = Router.router(vertx);
        Router api = Router.router(vertx);
        api.route().handler(BodyHandler.create());
        router.mountSubRouter("/api/v1", api);

        router.route("/*").handler(StaticHandler.create());

        api.route().handler(CorsHandler.create("*")
                .allowedMethod(io.vertx.core.http.HttpMethod.GET)
                .allowedMethod(io.vertx.core.http.HttpMethod.POST)
                .allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS)
                .allowedHeader("Access-Control-Request-Method")
                .allowedHeader("Access-Control-Allow-Credentials")
                .allowedHeader("Access-Control-Allow-Origin")
                .allowedHeader("Access-Control-Allow-Headers")
                .allowedHeader("X-Requested-With")
                .allowedHeader("Content-Type"));


        api.post(API_USERS).handler(this::createUser);
        api.put(API_USER_BY_ID).handler(this::updateUser);
        api.delete(API_USER_BY_ID).handler(this::deleteUser);
        api.get(API_USERS).handler(this::getUsers);
        api.get(API_USER_BY_ID).handler(this::getUser);

        SwaggerGenerator.publishSwagger(api, new ApiKeyAuthDefinition("Authorization", In.HEADER),"/spec",
         "Authorization Service", "1.0.0", "/api/v1");

        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(config().getInteger("http.port", 8080));
    }

    @ApiOperation(value = "Get user list", httpMethod = "GET",  response = User.class, responseContainer = "List",
            authorizations = @Authorization(value = "auth"),
            notes = "Get the list of users from DB")
    @ApiResponses(value = {
            @ApiResponse(code = SC_INTERNAL_SERVER_ERROR, message = "Internal Server Error"),
            @ApiResponse(code = SC_SERVICE_UNAVAILABLE, message = "Service Unavailable")})
    @VertxPath("/users")
    private void getUsers(RoutingContext routingContext) {
        routingContext.response()
                .putHeader("content-type", "application/json")
                .setStatusCode(200)
                .end(Json.encodePrettily(users));
    }

    @ApiOperation(value = "Create user", httpMethod = "POST",  response = User.class,
            authorizations = @Authorization(value = "auth"),
            notes = "Creates the user object to DB")
    @ApiResponses(value = {
            @ApiResponse(code = SC_INTERNAL_SERVER_ERROR, message = "Internal Server Error"),
            @ApiResponse(code = SC_SERVICE_UNAVAILABLE, message = "Service Unavailable")})
    @ApiImplicitParams(value = {
            @ApiImplicitParam(dataType = "com.forsythe.vertxswagger.domain.User", paramType = "body")
    })
    @VertxPath("/users")
    private void createUser(RoutingContext routingContext) {
        saveUser(routingContext);
    }

    @ApiOperation(value = "Update user", httpMethod = "PUT", response = User.class,
            authorizations = @Authorization(value = "auth"),
            notes = "Updates the user object to DB")
    @ApiResponses(value = {
            @ApiResponse(code = SC_INTERNAL_SERVER_ERROR, message = "Internal Server Error"),
            @ApiResponse(code = SC_SERVICE_UNAVAILABLE, message = "Service Unavailable")})
    @ApiImplicitParams(value = {
            @ApiImplicitParam(dataType = "com.forsythe.vertxswagger.domain.User", paramType = "body"),
            @ApiImplicitParam(paramType = "path", name = "id", required = true, type = "integer")
    })
    @VertxPath("/users/:id")
    private void updateUser(RoutingContext routingContext) {
        saveUser(routingContext);
    }

    @ApiOperation(value = "Delete user", httpMethod = "DELETE", notes = "Deletes the user object from DB",
            authorizations = @Authorization(value = "auth")
    )
    @ApiResponses(value = {
            @ApiResponse(code = SC_INTERNAL_SERVER_ERROR, message = "Internal Server Error"),
            @ApiResponse(code = SC_SERVICE_UNAVAILABLE, message = "Service Unavailable")})
    @ApiImplicitParams(value = {
            @ApiImplicitParam(paramType = "path", name = "id", required = true, type = "integer")
    })
    @VertxPath("/users/:id")
    private void deleteUser(RoutingContext routingContext) {
        routingContext.response()
                .putHeader("content-type", "application/json")
                .setStatusCode(200)
                .end();
    }

    @ApiOperation(value = "Get user", httpMethod = "GET", notes = "Get the user object from DB",
            authorizations = @Authorization(value = "auth"),
            response = User.class)
    @ApiResponses(value = {
            @ApiResponse(code = SC_INTERNAL_SERVER_ERROR, message = "Internal Server Error"),
            @ApiResponse(code = SC_SERVICE_UNAVAILABLE, message = "Service Unavailable")})
    @ApiImplicitParams(value = {
            @ApiImplicitParam(paramType = "path", name = "id", required = true, dataType = "integer", value = "User ID")
    })
    @VertxPath("/users/:id")
    private void getUser(RoutingContext routingContext) {
        routingContext.response()
                .putHeader("content-type", "application/json")
                .setStatusCode(200)
                .end();
    }

    private void saveUser(RoutingContext routingContext) {
        final User user = Json.decodeValue(routingContext.getBodyAsString(),
                User.class);
        routingContext.response()
                .putHeader("content-type", "application/json")
                .setStatusCode(200)
                .end(Json.encodePrettily(user));
    }
}
