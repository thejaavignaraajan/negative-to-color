# Build Stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Limit Maven memory usage to fit comfortably within 512MB free tier
ENV MAVEN_OPTS="-Xmx320m -XX:+UseSerialGC"

COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests --no-transfer-progress

# Run Stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

# Configure lightweight JVM settings for 512MB RAM:
# - SerialGC reduces memory overhead of garbage collection
# - Max heap capped at 320MB leaving headroom for OS and Metaspace
ENTRYPOINT ["sh", "-c", "java -Xms128m -Xmx320m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -jar app.jar --server.port=${PORT}"]
