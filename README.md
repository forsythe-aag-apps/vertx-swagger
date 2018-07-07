# vertx-swagger
Vert.x Swagger Support


The project contains example of using Swagger annotations to generate Swagger API from Vert.x routes.

To add Swagger support for the vert.x project you need to do the following:
 
Add dependencies to swagger artifacts to gradle:

```
compile 'io.swagger:swagger-core:1.5.20'
compile 'io.swagger:swagger-annotations:1.5.20'
compile 'io.swagger:swagger-models:1.5.20'
compile group: 'io.swagger', name: 'swagger-servlet', version: '1.5.20'
```

Add SwaggerGenerator, VertPath classes to the project.

The you can prepare manually all necessary information to be exposed from Swagger using ApiModelProperty 
to describe the fields of the model for requests or responses of the API. Also add necessary annotations to Vertx 
route handlers. Example can be found in RestVerticle class:

```
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
```

@VertPath annotation is important to distinguish between different methods with the same class as it allows 
to identify what is method should be exposed for each Vertx route handler.
  
At the end of route configuration you should add call for generation API info:

```
SwaggerGenerator.publishSwagger(api, new ApiKeyAuthDefinition("Authorization", In.HEADER),"/spec",
         "Authorization Service", "1.0.0", "/api/v1");
```
