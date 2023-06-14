# Stage 1 : Build with Gradle
#FROM openjdk:17.0.1-jdk-slim as build
FROM gradle:8.1-jdk17-alpine as build

WORKDIR /app

COPY . /app

# Build the application
RUN gradle build

# Stage 2 : Create the docker final image
FROM openjdk:17.0.1-jdk-slim

RUN apt-get update && apt-get install -y wget unzip

# Download protoc
RUN wget https://github.com/protocolbuffers/protobuf/releases/download/v3.19.4/protoc-3.19.4-linux-x86_64.zip

# Unzip protoc
RUN unzip protoc-3.19.4-linux-x86_64.zip -d /usr/local

ENV PATH="$PATH:/usr/local/bin"

ENV LANGUAGE='en_US:en'

# Copy artifacts from build stage
COPY --from=build /home/gradle/src/build/quarkus-app/lib/ /deployments/lib/
COPY --from=build /home/gradle/src/build/quarkus-app/*.jar /deployments/
COPY --from=build /home/gradle/src/build/quarkus-app/app/ /deployments/app/
COPY --from=build /home/gradle/src/build/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8090
USER 185
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"