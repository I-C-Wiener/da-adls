# --- Stage 1: build ---
FROM eclipse-temurin:21-jdk-alpine AS builder

RUN apk add --no-cache bash curl
RUN curl -sL "https://github.com/sbt/sbt/releases/download/v1.9.7/sbt-1.9.7.tgz" | tar xz -C /usr/local
ENV PATH="/usr/local/sbt/bin:${PATH}"

WORKDIR /app

# Cache dependencies before copying source
COPY build.sbt ./
COPY project/build.properties project/plugins.sbt ./project/
RUN sbt update

COPY . .
RUN sbt dist

# --- Stage 2: runtime ---
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache bash

WORKDIR /app

COPY --from=builder /app/target/universal/*.zip /tmp/app.zip
RUN unzip /tmp/app.zip -d /app && mv /app/chat-server-* /app/dist && rm /tmp/app.zip

ENV JAVA_OPTS="-Xmx512m -Xms128m"

EXPOSE 9000

CMD ["/app/dist/bin/chat-server", "-Dhttp.port=9000", "-Dplay.evolutions.autoApply=true"]
