# ---------- build stage ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Сначала только pom для кэша зависимостей
COPY pom.xml .
# Кэшируем ~/.m2, чтобы при правках src не тянуть всё заново
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests dependency:go-offline

# Теперь исходники
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests clean package

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre
# (опц.) неблокирующие шрифты/локали
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Dfile.encoding=UTF-8"

WORKDIR /app
# копируем fat-jar
ARG JAR_FILE=/workspace/target/*-SNAPSHOT.jar
COPY --from=build ${JAR_FILE} /app/app.jar

EXPOSE 8080
# Если используешь прокси — можно прокинуть через JAVA_OPTS в docker run
ENV JAVA_OPTS=""

# exec-форма, без shell; переменные для JVM опций берём из JAVA_TOOL_OPTIONS/JAVA_OPTS
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]