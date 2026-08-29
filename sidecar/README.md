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
| Kafka topic your microservice **consumes** commands from | `book-hotel` |
| Kafka topic your microservice **publishes** to      | `book-hotel.engine`  |
| BPMN message name (catch event and/or start event) | `book-hotel.engine`  |

Nothing else is required — no registration, no config file, no deployment
step for the sidecar itself. As soon as a process definition with a
matching external-task topic or message name is deployed, it works. If you
trigger the command via the sidecar's HTTP dispatch endpoint instead of an
external task (see below), that same capability name is the path segment:
`POST /dispatch/book-hotel`.

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

**To ask a capability to do something**, pick one of two service task types
— both land on the exact same `<topic>.commands` message, so your
microservice never knows or cares which one was used:

- **External task** (pull): topic name `book-hotel`. All process variables
  visible at that point are sent to the capability automatically — you
  don't need to list them. The sidecar picks the task up on its own; if the
  sidecar or Kafka is briefly unavailable, the task just waits and gets
  picked up on the next attempt. Prefer this by default.
- **HTTP Connector** (push): configure the service task's connector with
  - URL: `http://sidecar:8080/dispatch/book-hotel`
  - Method: `POST`
  - Header `Business-Key`: `${execution.processBusinessKey}`
  - Payload: whichever process variables you want to send, e.g.
    `{"origin": "${origin}", "destination": "${destination}"}`

  This calls the sidecar directly and synchronously, with no polling delay,
  but you have to list the variables to send yourself, and the process
  briefly waits on the call while it's in flight. Prefer this only where
  that immediacy matters more than the simplicity of the external task.

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
- Kafka topic: `<topic>.`
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
