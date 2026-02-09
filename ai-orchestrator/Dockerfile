FROM eclipse-temurin:17-jre
WORKDIR /app
# Copy the locally built JAR directly
COPY target/ai-orchestrator-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
