FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY --chmod=755 mvnw ./mvnw
COPY pom.xml ./
COPY src/main/ src/main/

# PostgreSQL/Testcontainers verification runs separately, not inside the image build.
RUN ./mvnw --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-noble AS runtime
WORKDIR /app

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

COPY --from=build /build/target/*.jar /app/app.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
