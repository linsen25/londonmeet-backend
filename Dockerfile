FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY common/pom.xml common/pom.xml
COPY pojo/pom.xml pojo/pom.xml
COPY server/pom.xml server/pom.xml

RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY common/src/ common/src/
COPY pojo/src/ pojo/src/
COPY server/src/ server/src/

RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /workspace/server/target/server-0.0.1-SNAPSHOT.jar server/target/server-0.0.1-SNAPSHOT.jar

EXPOSE 8080

CMD ["java", "-jar", "server/target/server-0.0.1-SNAPSHOT.jar"]
