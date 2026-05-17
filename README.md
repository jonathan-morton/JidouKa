[![](https://jitpack.io/v/dev.jidouka/jidouka.svg)](https://jitpack.io/#dev.jidouka/jidouka)

# JidouKa

JidouKa is a Kotlin library for writing smart home automations and interacting with Home Assistant. Writing automations
is intended to be as simple or as complex as you would like to make your home system. JidouKa automations use blocks
that are modeled after the familiar paradigms in Home Assistant automations. There are triggers, conditions, and
actions. However, you can use the full power of the Kotlin language and ecosystem to write your automations.

## Your first automation

The basics of an automation are the same as a standard Home Assistant automation. We have a trigger that will start an
automation. There is an optional conditions block that can be checked to ensure an automation should or should not run.
Finally, we have an actions block that will be the actual calls that run.

```kotlin
fun main() = runBlocking {
    val haAccessToken = "super secret long lived access token" //Your long-lived access token
    val app: JidoukaApplication = Jidouka.initialize {
        // Create the configuration for your Home Assistant instance
        haConfiguration = HomeAssistantConfiguration(
            accessToken = haAccessToken
        )

        automations {
            automation(id = "print_hello_world") {
                val contactSensorEntity =
                    entity("binary_sensor.living_room_door_contact") // A contact sensor entity on the living room door

                triggers {
                    time.every(10.minutes) // Action will run every 10 minutes

                    state(contactSensorEntity) {
                        val contactSensorOpenState = "on"
                        it?.state.equals(
                            contactSensorOpenState,
                            ignoreCase = true
                        ) // Action will run when the door is opened
                    }
                }

                conditions {
                    condition {
                        time.isBetween(
                            start = DayOfWeek.MONDAY,
                            end = DayOfWeek.FRIDAY
                        ) // A condition that only allows the actions to run Monday through Friday
                    }
                }

                actions {
                    log.info { "Hello world!" } // logs out Hello world!
                    delay(2.minutes)
                    log.info { "ハロー ワールド!" }
                }
            }
        }
    }
    app.start()
}
```

The above is a complete example of a running JidouKa application. We initialize the app with a `Jidouka.initialize { }`
block, where we can set various configurations. In this example only the mandatory `accessToken` is set.

In the `automations { }` block we create a simple automation. Automations created in the `automations { }` block are
automatically registered to JidouKa. The automation is given a mandatory unique `id` that is used to identify all
automations. An `entity` is created from the Home Assistant entity ID that corresponds to the living room contact
sensor. We can access entities in any of the lambda blocks inside of an automation.

In the `triggers` block we create two triggers. We use the time trigger `every` to trigger every this automation every
10 minutes. `state()` is given the `contactSensorEntity`. When the entity state becomes `"on"` the state trigger will
ALSO cause the automation to run.

The optional `conditions` block has one time condition. The actions block will only run Monday through Friday.

`actions` contains our final block. We log to the console `Hello world!` first. All blocks can use standard Kotlin, in
this case we use delay to wait 2 minutes. We then log to the console hello world in Japanese.

Finally, we start the JidouKa application with the `JidoukaApplication` returned from the initialize function.

## Installation

```kotlin
repositories {
    // ...
    maven { url = uri("https://jitpack.io") }
}
```

```kotlin
plugins {
    // ...
    kotlin("plugin.serialization") version "2.3.21"
}

dependencies {
    implementation("dev.jidouka:jidouka:0.3.0")
}
```

## Deploying to a Home Server

When you are ready to run your code on your server, you can use Docker to make deployment fairly easy

### 1. Add a `Dockerfile` to your project

Copy this into the root of your project:

```dockerfile
# Build stage
FROM gradle:8-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew installDist -PuseLocalJidouka=false --no-daemon && \
    cp -r build/install/*/* /app/dist/

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/dist .
VOLUME ["/app/logs"]
CMD ["/bin/sh", "-c", "exec ./bin/$(ls ./bin | head -1)"]
```

### 2. Add a `docker-compose.yml`

With `my-jidouka` as an example name for your project:

```yaml
services:
  my-jidouka:
    image: my-jidouka:latest
    restart: unless-stopped
    env_file: .env
    volumes:
      - ./logs:/app/logs
```

### 3. Create a `.env` file

Create a `.env` file in your project root with your Home Assistant credentials. **Do not commit this file to git** .env
should be added to your .gitignore

```
HA_HOST=homeassistant.local
HA_PORT=8123
HA_ACCESS_TOKEN=your_long_lived_access_token_here
```

### 4. Build the image for your target machine

On your development machine, use `docker buildx` to cross-compile for your server's architecture.

```bash
# One-time setup
docker buildx create --use

# For ARM64 (Odroid N2, Raspberry Pi 4, etc.)
docker buildx build --platform linux/arm64 -t my-jidouka:latest --output type=docker,dest=my-jidouka.tar .

# For x86-64
docker buildx build --platform linux/amd64 -t my-jidouka:latest --output type=docker,dest=my-jidouka.tar .
```

### 5. Transfer and start

```bash
# Copy the image, env file, and compose file to your server
scp my-jidouka.tar .env docker-compose.yml user@your-server:~/my-jidouka/

# Load the image and start
ssh user@your-server "cd ~/my-jidouka && docker load -i my-jidouka.tar && docker compose up -d"
```

### Updating

When you make changes, rebuild and redeploy:

```bash
docker buildx build --platform linux/arm64 -t my-jidouka:latest --output type=docker,dest=my-jidouka.tar .
scp my-jidouka.tar user@your-server:~/my-jidouka/
ssh user@your-server "cd ~/my-jidouka && docker load -i my-jidouka.tar && docker compose restart"
```

## Roadmap

- Generate entities and services from the actual Home Assistant instance.
    - Instead of writing `getEntity("Light.kitchen_sink", Domain.Light)` you will be able to just write
      `Light.kitchenSink` and have autocomplete available on a fully typed entity.
- Top level support for all [automation triggers](https://www.home-assistant.io/docs/automation/trigger) not covered by
  implemented triggers
    - MQTT and Webhook support are planned
    - Specific sun support is being evaluated.
        - The Home Assistant standard sun sensor updates as the event passes, which may create bugs for offsets in the
          future. The [sun2 sensor](https://github.com/pnbruckner/ha-sun2) updates at a fixed time and is recommended
          for
          now.
    - The numeric state trigger, for example, is not planned. It is effectively covered by a state trigger
- Add annotations for automations to remove the boilerplate of adding and registering an automation
- Creation of persistent Home Assistant services and states that survive a restart of Home Assistant

## Warnings

This project is going to be in a state of flux while I refine the DSL and expand the feature set. There almost certainly
will be breaking changes, but I will do my best to communicate those changes in the release notes

### LLM Usages

There are absolutely no vibes in this project. I have been programming since childhood and have been a professional
developer for more than a decade. JidouKa was created because I wanted to use one of my favorite self-hosted apps with
my favorite programming language. I enjoy writing software and thought it would be fun to expand my knowledge while
making something tangible for my home.

However, this project has been LLM Assisted. To keep this project enjoyable to write in my free time, I tend to plan out
the features and then have the LLM evaluate the plan and make suggestions. I then evaluate how the plan will be
implemented, make refinements, and describe what should be done in small logical pieces, working directly in the IDE
with any edits. If it falls on its face, I just write it myself. If I just feel like programming for fun in a flow
state, I just write it myself.

When I use LLM output, I treat it like a code review and have no issues throwing away or rewriting large sections of
poorly architected code. The LLMs are a tool that can help speed me up, but they are just a tool that I can use.

There will never be vibes in JidouKa.