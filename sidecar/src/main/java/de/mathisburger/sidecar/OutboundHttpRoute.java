package de.mathisburger.sidecar;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;

public class OutboundHttpRoute extends RouteBuilder {

    @Override
    public void configure() {

        restConfiguration()
            .component("jetty")
            .host("0.0.0.0")
            .port("{{http.port:8080}}")
            .bindingMode(RestBindingMode.off);

        onException(IllegalArgumentException.class)
            .handled(true)
            .log(LoggingLevel.WARN, "dispatch rejected: ${exception.message}")
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
            .setBody(simple("${exception.message}"));

        onException(Exception.class)
            .handled(true)
            .log(LoggingLevel.WARN, "dispatch error: ${exception.message}")
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(502))
            .setBody(simple("${exception.message}"));

        rest("/dispatch")
            .post("/{topic}")
                .to("direct:dispatch");

        from("direct:dispatch")
            .routeId("outbound-http-dispatch")
            .process(Mediation::httpToCommand)
            .toD("kafka:${header.kafkaTopic}")
            .log(LoggingLevel.INFO, "published command via HTTP dispatch on ${header.kafkaTopic}")
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(202))
            .setBody(constant(""));
    }
}
