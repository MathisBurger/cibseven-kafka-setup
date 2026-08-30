# CIBseven + Kafka mediation

Connects CIBseven BPMN process models to any microservice over Kafka,
without per-capability code or configuration. Two pieces:

- **`sidecar/`** — a Camel-based mediation sidecar. Translates between the
  engine's REST API and Kafka, driven entirely by naming convention.
- **`processengine/`** — a CIBseven Tomcat image with one addition: every
  HTTP Connector request automatically carries a `Business-Key` header, so
  BPMN models don't have to set it by hand.

See each folder's own README for the details specific to it. This one is
the short version that applies regardless of how you've wired things up.

## The naming convention

Pick a name for your capability, e.g. `book-hotel`. It drives everything:
the external-task topic, the Kafka command topic, the HTTP dispatch path,
and the BPMN message name are all just that one name (see `sidecar/README.md`
for the full table).

**Every Kafka topic ending in `.engine` is forwarded to the engine as a
message correlation, always with the `.engine` suffix stripped off to get
the BPMN message name.** A message published to `book-hotel.engine`
correlates (or starts) message `book-hotel`, not `book-hotel.engine`. The
business key is always the Kafka record key on both sides.

## Triggering a capability: two equivalent ways

A BPMN service task can ask a capability to do something either way — both
end up publishing the exact same command message, so the microservice on
the other end never knows or cares which one was used:

- **External task** (pull): the sidecar polls the engine and picks up the
  work on its own. No code, no header, nothing to configure beyond the
  topic name.
- **HTTP dispatch** (push): `POST /dispatch/<topic>` directly on the
  sidecar, e.g. from a BPMN HTTP Connector service task. Immediate, no
  polling delay — at the cost of having to list the payload yourself.

Use whichever fits a given service task; mixing both across a process is
fine.

## On this being HTTP-based

The sidecar and the engine talk over plain HTTP — external-task
fetch/complete, message correlation, and the push-style dispatch endpoint
are all REST calls. That sounds like it should be slower than something
more "native," but in practice it isn't a concern here: all of that traffic
stays inside the same Docker network, where latency between containers is
negligible. Because of that, this scales out horizontally without a second
thought — run more sidecar replicas, with no shared state between them to
coordinate:

- Inbound `*.engine` topics are a real Kafka consumer group, so replicas
  split the partitions between them automatically.
- Outbound external tasks are self-coordinating too: CIBseven's own task
  locking means two replicas polling at once just compete for work, never
  duplicate it.
- Outbound HTTP dispatch is stateless — put any number of replicas behind
  a load balancer, no session affinity needed.

Of course, keep in mind that scaling out the sidecar horizontally increases network latency and therefore can affect performance.
