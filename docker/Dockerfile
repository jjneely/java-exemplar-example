# multi-stage dockerfile

# first a temporary container to run layertools
FROM adoptopenjdk/openjdk16:alpine as builder
WORKDIR application
ARG APP_NAME=custom-metrics-demo
ARG APP_VERSION=0.0.2
COPY ./build/libs/${APP_NAME}-${APP_VERSION}.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# then the final container with the layers copied from the last container
FROM adoptopenjdk/openjdk16:alpine
WORKDIR application
# the order is important for performance
COPY --from=builder application/dependencies/ ./
COPY --from=builder application/spring-boot-loader/ ./
COPY --from=builder application/snapshot-dependencies/ ./
COPY --from=builder application/application/ ./

# Copy in Java Agents
ARG OTEL_AGENT_VERSION=1.9.1
COPY ./build/otel/opentelemetry-javaagent-all-${OTEL_AGENT_VERSION}.jar opentelemetry-javaagent-all.jar

VOLUME /tmp

COPY docker/launchSpringApp.sh .
RUN chmod +x launchSpringApp.sh
CMD ["sh", "-c", "exec ./launchSpringApp.sh "]

EXPOSE 8080
