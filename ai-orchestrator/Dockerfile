FROM eclipse-temurin:17-jdk as build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw -v || true
# Use Maven Wrapper if you add it, otherwise use mvn (requires Maven in image).
# For simplicity in this scaffold, we use mvn from a Maven image in real usage.
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
