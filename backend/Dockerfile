FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests -Dquarkus.package.jar.type=fast-jar package

FROM eclipse-temurin:25-jre
WORKDIR /work/
COPY --from=build /workspace/target/quarkus-app/lib/ /work/lib/
COPY --from=build /workspace/target/quarkus-app/*.jar /work/
COPY --from=build /workspace/target/quarkus-app/app/ /work/app/
COPY --from=build /workspace/target/quarkus-app/quarkus/ /work/quarkus/
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","quarkus-run.jar"]
