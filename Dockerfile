FROM maven:3-jdk-8 AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean install

FROM openjdk:8-jdk-alpine
COPY --from=build /home/app/target/mashabot-1.0.0-SNAPSHOT.jar /usr/local/lib/mashabot.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/local/lib/mashabot.jar"]