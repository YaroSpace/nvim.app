FROM clojure:tools-deps-trixie-slim AS base

FROM base AS build
# RUN apt update
# apt install -y nodejs npm && \
# apt-get clean

WORKDIR /build

COPY deps.edn .
COPY build.clj .
COPY src ./src
COPY resources ./resources

RUN clj -T:build uber

FROM eclipse-temurin:24-alpine AS prod

# Install Clojure in the image

# RUN curl -O https://download.clojure.org/install/linux-install-1.11.1.1413.sh && \
#     chmod +x linux-install-1.11.1.1413.sh && \
#     ./linux-install-1.11.1.1413.sh && \
#     rm linux-install-1.11.1.1413.sh

WORKDIR /app

COPY --from=build /build/target/nvim-app-*.jar app.jar
# COPY deps.edn deps.edn


ENTRYPOINT ["java", "-jar", "app.jar"]
