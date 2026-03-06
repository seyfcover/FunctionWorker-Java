FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Fat JAR kopyala
COPY target/example-app-1.0.0.jar app.jar

# Sağlık kontrolü
HEALTHCHECK --interval=10s --timeout=5s --retries=3 \
  CMD pgrep -f "app.jar" > /dev/null || exit 1

# Varsayılan: worker modu
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
            "-jar", "app.jar"]

CMD ["worker"]
