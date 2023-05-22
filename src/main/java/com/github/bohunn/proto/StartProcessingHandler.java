package com.github.bohunn.proto;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.vertx.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class StartProcessingHandler {

    @Inject
    EventBus eventBus;

    @Route(path = "/start-processing", methods = Route.HttpMethod.GET)
    public void handle(RoutingContext context) {
        eventBus.send("process-protobufs", "start");
        context.response().setStatusCode(HttpResponseStatus.OK.code()).end("Processing started");
    }
}
