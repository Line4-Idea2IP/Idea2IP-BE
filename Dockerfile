FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN sed -i 's/\r$//' gradlew && bash gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy
LABEL org.opencontainers.image.source="https://github.com/Line4-Idea2IP/Idea2IP-BE"
RUN apt-get update && apt-get install -y --no-install-recommends curl fonts-nanum \
    && rm -rf /var/lib/apt/lists/* \
    && font="$(find /usr/share/fonts -name NanumGothic.ttf -print -quit)" \
    && test -n "$font" && install -D -m 644 "$font" /opt/idea2ip/NanumGothic.ttf \
    && install -d -o 10001 -g 10001 /data/uploads /data/report-pdfs
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
