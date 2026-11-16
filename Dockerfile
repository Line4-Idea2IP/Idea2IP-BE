FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
# Gradle and the dependencies get their own layer, reused until the build files change. Maven Central
# rate-limits shared CI runners (HTTP 429), so a failed download is retried after a pause.
RUN sed -i 's/\r$//' gradlew \
    && for attempt in 1 2 3; do \
         bash gradlew --no-daemon --quiet resolveDependencies && break; \
         [ "$attempt" -eq 3 ] && exit 1; \
         echo "Dependency download failed (attempt $attempt of 3); retrying" >&2; \
         sleep $((attempt * 30)); \
       done
COPY src ./src
RUN bash gradlew --no-daemon --offline bootJar

FROM eclipse-temurin:21-jre-jammy
LABEL org.opencontainers.image.source="https://github.com/Line4-Idea2IP/Idea2IP-BE"
RUN apt-get update && apt-get install -y --no-install-recommends curl fonts-nanum \
    && rm -rf /var/lib/apt/lists/* \
    && font="$(find /usr/share/fonts -name NanumGothic.ttf -print -quit)" \
    && test -n "$font" && install -D -m 644 "$font" /opt/idea2ip/NanumGothic.ttf \
    && bold="$(find /usr/share/fonts -name NanumGothicBold.ttf -print -quit)" \
    && test -n "$bold" && install -D -m 644 "$bold" /opt/idea2ip/NanumGothicBold.ttf \
    && install -d -o 10001 -g 10001 /data/uploads /data/report-pdfs
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
