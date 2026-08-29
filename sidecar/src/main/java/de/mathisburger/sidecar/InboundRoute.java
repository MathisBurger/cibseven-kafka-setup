package de.mathisburger.sidecar;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;

public class InboundRoute extends RouteBuilder {

    @Override
    public void configure() {

        onException(Exception.class)
            .handled(true)
            .log(LoggingLevel.WARN, "inbound error: ${exception.message}");

        from("kafka:.*" + Mediation.ENGINE_SUFFIX.replace(".", "\\.")
                + "?topicIsPattern=true&groupId={{worker.id}}&autoOffsetReset=earliest"
                + "&metadataMaxAgeMs={{inbound.metadataMaxAgeMs:5000}}")
            .routeId("inbound-correlate")
            .process(Mediation::messageToCorrelation)
            .log(LoggingLevel.INFO, "correlating message ${header.messageName} for business key ${header.businessKey}")
            .setHeader("CamelHttpMethod", constant("POST"))
            .setHeader("Content-Type", constant("application/json"))
            .to("{{engine.rest}}/message");
    }
}
