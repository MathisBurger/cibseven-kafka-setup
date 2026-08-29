# Using the sidecar

This sidecar connects your BPMN process models to your microservices over
Kafka. You never configure it and you never touch its code — you plug in a
new capability purely by naming things consistently in your process model
and your microservice. This document describes that contract: what to name
things, what messages look like, and how to model both directions.

## The naming rule

Pick a name for your capability, e.g. `book-hotel`. That one name drives
everything:

| What                                              | Name                |
|----------------------------------------------------|----------------------|
| BPMN external-task topic (on your service task)    | `book-hotel`         |
| Kafka topic your microservice **consumes** commands from | `book-hotel.commands` |
| Kafka topic your microservice **publishes** to      | `book-hotel.engine`  |
| BPMN message name (catch event and/or start event) | `book-hotel.engine`  |

Nothing else is required — no registration, no config file, no deployment
step for the sidecar itself. As soon as a process definition with a
matching external-task topic or message name is deployed, it works.

## The business key

Every process instance must have a **business key**
(e.g. `T-1`, an order id, a booking id — whatever identifies the case).

- The business key is always the **Kafka record key**, on both the command
  and the reply/trigger side.
- Your microservice never needs to look inside the message payload for a
  correlation id — just echo back the key you received on the command.
- If you're starting a new process instance from your microservice instead
  of replying to one, you choose the business key yourself.

## Modeling the process (BPMN)

**To ask a capability to do something:** add a service task, external task
type, with a topic name (e.g. `book-hotel`). All process variables visible
at that point are sent to the capability — you don't need to list them.

**To wait for the capability's answer:** add a message catch event whose
message name is exactly `<topic>.engine` (e.g. `book-hotel.engine`). When
the reply arrives, every field in it becomes a process variable, and the
flow continues from that point.

**To let a capability start a new process instead:** add a message *start*
event whose message name is `<topic>.engine`. Nothing else about the model
changes — a microservice publishing to that topic starts a brand new
instance, with the message's fields as the instance's initial variables and
its key as the instance's business key.

A single topic name can be used for both a catch event (resume an existing
case) and a start event (open a new one) if that fits your process — the
sidecar doesn't care which one ends up matching; the engine decides.

## Building the microservice

**Consume commands:**
- Kafka topic: `<topic>.commands`
- Key: the business key of the process instance that asked for the work
- Value: a flat JSON object of the process variables at the time the task
  was picked up, e.g.:
  ```json
  {"origin": "BER", "destination": "CDG", "passenger": "Ada"}
  ```

**Publish the result (or start a flow):**
- Kafka topic: `<topic>.engine`
- Key: the business key — the same one the command carried, if you're
  answering it; a new one of your choosing, if you're starting a process
- Value: a flat JSON object of whatever should become process variables,
  e.g.:
  ```json
  {"flightConfirmation": "FL-999"}
  ```

That's the entire integration surface. No SDK, no client library, no
handshake — just consume/produce plain JSON on two conventionally-named
topics.

## Worked example: a "flight" capability

1. BPMN: a service task with external-task topic `reserve-flight`, followed
   by a message catch event named `reserve-flight.engine`. The process is
   started with business key `T-1` and variables `origin`, `destination`,
   `passenger`.

2. The sidecar publishes to `reserve-flight.commands`:
   ```
   key:   T-1
   value: {"origin":"BER","destination":"CDG","passenger":"Ada"}
   ```

3. Your flight microservice does its work and publishes to
   `reserve-flight.engine`:
   ```
   key:   T-1
   value: {"flightConfirmation":"FL-999"}
   ```

4. The waiting instance resumes with `flightConfirmation = "FL-999"` set.

For a capability that starts flows instead (e.g. `onboard-customer`): skip
step 1's external task, model a message start event named
`onboard-customer.engine`, and have any microservice publish
`{"customerName": "Ada"}` keyed by a new business key like `C-1` whenever it
wants to kick off a new instance.

## Trying it locally

```bash
docker compose up --build
```

- Webapps: http://localhost:8080/webapp (login `demo` / `demo`)
- REST API: http://localhost:8080/engine-rest

You can stand in for a microservice with the Kafka CLI to sanity-check a
process model before the real microservice exists:

```bash
# watch commands land
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 --topic reserve-flight.commands --from-beginning \
  --property print.key=true --property key.separator=" | "

# send a reply / trigger
docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server kafka:9092 --topic reserve-flight.engine \
  --property "parse.key=true" --property "key.separator=|" \
  <<< 'T-1|{"flightConfirmation":"FL-999"}'
```

## Things to keep in mind

- Give every process instance a business key — without one, nothing can be
  correlated back to it.
- A new capability's `*.engine` topic is picked up within a few seconds of
  its first message, no restart needed.
- Keep business decisions in the process model. A capability's message
  should just report a fact (or ask to start a flow) — routing logic,
  branching, and sequencing belong in the `.bpmn`, not in what you publish.
- Correlating purely by business key means two external tasks outstanding
  at the same time on the *same* process instance can't be told apart by an
  incoming message alone — design your process so a case has at most one
  pending capability call at a time.
