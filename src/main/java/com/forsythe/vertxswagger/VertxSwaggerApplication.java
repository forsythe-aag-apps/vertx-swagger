package com.forsythe.vertxswagger;

import io.vertx.core.Vertx;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;

@SpringBootApplication
public class VertxSwaggerApplication {

    @Autowired
    private RestVerticle restVerticle;

	public static void main(String[] args) {
		SpringApplication.run(VertxSwaggerApplication.class, args);
	}

	@PostConstruct
    public void deployVerticles() {
        Vertx.vertx().deployVerticle(restVerticle);
    }
}
