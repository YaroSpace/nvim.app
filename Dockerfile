FROM eclipse-temurin:21-jre

RUN curl -O https://download.clojure.org/install/linux-install-1.11.1.1413.sh && \
    chmod +x linux-install-1.11.1.1413.sh && \
    ./linux-install-1.11.1.1413.sh && \
    rm linux-install-1.11.1.1413.sh

WORKDIR /app

COPY target/nvim-app-*.jar app.jar
COPY deps.edn deps.edn

ENTRYPOINT ["java", "-jar", "app.jar"]
