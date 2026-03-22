FROM eclipse-temurin:21-jre

LABEL org.opencontainers.image.authors="bootlabs"

# cd /app
WORKDIR /app

# Refer to Maven build -> finalName
ARG JAR_FILE=target/logging-spring-webflux-*.jar

# cp target/logging-spring-webflux-0.0.1-SNAPSHOT.jar /app/logging-spring-webflux.jar
COPY ${JAR_FILE} logging-spring-webflux.jar

# java -jar /app/logging-spring-webflux.jar
CMD ["java", "-jar", "-Xmx1024M", "/app/logging-spring-webflux.jar"]

# Make port 8080 available to the world outside this container
EXPOSE 8080
