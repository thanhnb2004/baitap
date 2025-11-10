FROM eclipse-temurin:21-jdk
ARG JAR_FILE=target/BackendServer-1.0-SNAPSHOT.jar
COPY ${JAR_FILE} backend-server.jar
ENTRYPOINT ["java", "-jar", "/backend-server.jar"]
