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