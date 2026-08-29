package de.mathisburger.sidecar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConstants;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Mediation {

    private static final ObjectMapper M = new ObjectMapper();
    public static final String ENGINE_SUFFIX = ".engine";

    public static final String BUSINESS_KEY_HEADER = "Business-Key";

    private Mediation() {
    }

    public static void fetchAndLockRequest(Exchange ex) throws Exception {
        String topic = ex.getProperty("topic", String.class);
        String workerId = ex.getContext().resolvePropertyPlaceholders("{{worker.id}}");
        String maxTasks = ex.getContext().resolvePropertyPlaceholders("{{externaltask.maxTasks}}");
        String lockDuration = ex.getContext().resolvePropertyPlaceholders("{{externaltask.lockDuration}}");

        Map<String, Object> topicSpec = new LinkedHashMap<>();
        topicSpec.put("topicName", topic);
        topicSpec.put("lockDuration", Long.parseLong(lockDuration));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("workerId", workerId);
        request.put("maxTasks", Integer.parseInt(maxTasks));
        request.put("topics", List.of(topicSpec));

        ex.getIn().setHeader("CamelHttpMethod", "POST");
        ex.getIn().setHeader("Content-Type", "application/json");
        ex.getIn().setBody(M.writeValueAsString(request));
    }

    @SuppressWarnings("unchecked")
    public static void taskToCommand(Exchange ex) throws Exception {
        Map<String, Object> task = ex.getIn().getBody(Map.class);

        String taskId = (String) task.get("id");
        String topic = (String) task.get("topicName");
        String businessKey = (String) task.get("businessKey");
        Map<String, Object> vars = (Map<String, Object>) task.get("variables");

        if (businessKey == null) {
            throw new IllegalStateException("external task " + taskId + " on topic " + topic
                    + " belongs to a process instance with no business key; "
                    + "start the process with one so events can be correlated");
        }

        Map<String, Object> command = new LinkedHashMap<>();
        if (vars != null) {
            for (Map.Entry<String, Object> entry : vars.entrySet()) {
                command.put(entry.getKey(), unwrap(entry.getValue()));
            }
        }

        ex.getIn().setHeader("taskId", taskId);
        ex.getIn().setHeader("kafkaTopic", topic);
        ex.getIn().setHeader(KafkaConstants.KEY, businessKey);
        ex.getIn().setBody(M.writeValueAsString(command));
    }

    public static void httpToCommand(Exchange ex) throws Exception {
        String topic = ex.getIn().getHeader("topic", String.class);
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("dispatch request is missing the topic path segment");
        }

        String businessKey = ex.getIn().getHeader(BUSINESS_KEY_HEADER, String.class);
        if (businessKey == null || businessKey.isBlank()) {
            throw new IllegalArgumentException("dispatch request for topic " + topic
                    + " is missing the " + BUSINESS_KEY_HEADER + " header");
        }

        JsonNode body = M.readTree(ex.getIn().getBody(String.class));
        if (!body.isObject()) {
            throw new IllegalArgumentException("dispatch request for topic " + topic
                    + " must have a JSON object body");
        }

        ex.getIn().setHeader("kafkaTopic", topic);
        ex.getIn().setHeader(KafkaConstants.KEY, businessKey);
        ex.getIn().setBody(M.writeValueAsString(body));
    }

    public static void messageToCorrelation(Exchange ex) throws Exception {
        String topic = ex.getIn().getHeader(KafkaConstants.TOPIC, String.class);
        String businessKey = ex.getIn().getHeader(KafkaConstants.KEY, String.class);
        if (businessKey == null) {
            throw new IllegalStateException("message on topic " + topic
                    + " has no Kafka record key; the business key must be the record key");
        }

        JsonNode message = M.readTree(ex.getIn().getBody(String.class));
        Map<String, Object> processVariables = new LinkedHashMap<>();
        message.fields().forEachRemaining(f -> processVariables.put(f.getKey(), typed(f.getValue())));

        Map<String, Object> correlation = new LinkedHashMap<>();
        correlation.put("messageName", topic);
        correlation.put("businessKey", businessKey);
        correlation.put("processVariables", processVariables);

        ex.getIn().setHeader("businessKey", businessKey);
        ex.getIn().setHeader("messageName", topic);
        ex.getIn().setBody(M.writeValueAsString(correlation));
    }

    private static Map<String, Object> typed(JsonNode value) {
        String type;
        Object rawValue;
        if (value.isTextual()) {
            type = "String";
            rawValue = value.asText();
        } else if (value.isBoolean()) {
            type = "Boolean";
            rawValue = value.asBoolean();
        } else if (value.isIntegralNumber()) {
            type = "Long";
            rawValue = value.asLong();
        } else if (value.isFloatingPointNumber()) {
            type = "Double";
            rawValue = value.asDouble();
        } else if (value.isNull()) {
            type = "Null";
            rawValue = null;
        } else {
            type = "Json";
            rawValue = value.toString();
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("value", rawValue);
        v.put("type", type);
        return v;
    }

    @SuppressWarnings("unchecked")
    private static Object unwrap(Object v) {
        if (v instanceof Map) {
            return ((Map<String, Object>) v).get("value");
        }
        return v;
    }
}
