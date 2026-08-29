package de.mathisburger.sidecar;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import java.util.List;

public class OutboundRoute extends RouteBuilder {

    @Override
    public void configure() {

        onException(Exception.class)
            .handled(true)
            .log(LoggingLevel.WARN, "outbound error: ${exception.message}");

        from("timer:fetch?period={{outbound.pollMs:2000}}")
            .routeId("outbound-fetch")
            .setHeader("CamelHttpMethod", constant("GET"))
            .to("{{engine.rest}}/external-task/topic-names")
            .unmarshal().json(JsonLibrary.Jackson, List.class)
            .split(body())
                .setProperty("topic", body())
                .to("direct:fetchAndLock")
            .end();

        from("direct:fetchAndLock")
            .routeId("outbound-fetchAndLock")
            .process(Mediation::fetchAndLockRequest)
            .to("{{engine.rest}}/external-task/fetchAndLock")
            .unmarshal().json(JsonLibrary.Jackson, List.class)
            .split(body())
                .to("direct:publishCommand")
            .end();

        from("direct:publishCommand")
            .routeId("outbound-publish")
            .process(Mediation::taskToCommand)
            .toD("kafka:${header.kafkaTopic}")
            .log(LoggingLevel.INFO, "published command for external task ${header.taskId} on ${header.kafkaTopic}")
            .setHeader("CamelHttpMethod", constant("POST"))
            .setHeader("Content-Type", constant("application/json"))
            .setBody(simple("{\"workerId\":\"{{worker.id}}\"}"))
            .toD("{{engine.rest}}/external-task/${header.taskId}/complete");
    }
}
