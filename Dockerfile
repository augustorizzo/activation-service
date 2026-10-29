FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN sed -i 's/\r$//' mvnw .mvn/wrapper/maven-wrapper.properties \
    && chmod +x mvnw

COPY src/ src/

RUN ./mvnw -B clean verify


FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

RUN groupadd --system appgroup \
    && useradd --system --gid appgroup --no-create-home appuser

COPY --from=build --chown=appuser:appgroup \
    /workspace/target/activation-service-*.jar /app/app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]