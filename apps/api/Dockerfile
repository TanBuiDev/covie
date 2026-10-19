FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY apps/api/.mvn/ apps/api/.mvn/
COPY apps/api/mvnw apps/api/mvnw
COPY apps/api/pom.xml apps/api/pom.xml
RUN cd apps/api && sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY apps/api/src apps/api/src
RUN cd apps/api \
    && ./mvnw -B -DskipTests package \
    && JAR="$(find target -maxdepth 1 -type f -name '*.jar' ! -name 'original-*' -print -quit)" \
    && test -n "$JAR" \
    && cp "$JAR" /workspace/app.jar

FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache ffmpeg
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080 9091
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
