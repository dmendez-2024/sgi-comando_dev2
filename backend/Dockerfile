# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,id=sgi-comando-maven,target=/root/.m2/repository,sharing=locked \
    mvn -B -U -Daether.transport.http.retryHandler.count=5 -DskipTests -Dquarkus.package.jar.type=fast-jar package

FROM eclipse-temurin:25-jre
WORKDIR /work/
COPY --from=build /workspace/target/quarkus-app/lib/ /work/lib/
COPY --from=build /workspace/target/quarkus-app/*.jar /work/
COPY --from=build /workspace/target/quarkus-app/app/ /work/app/
COPY --from=build /workspace/target/quarkus-app/quarkus/ /work/quarkus/
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","quarkus-run.jar"]
