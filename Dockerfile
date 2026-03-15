# ─── Stage 1: Build Spring Boot app ───────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy maven wrapper and pom first for layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source and build
COPY src/ src/
RUN ./mvnw package -q -DskipTests


# ─── Stage 2: Runtime (Java + Node.js + Claude Code) ──────────────────────────
FROM eclipse-temurin:21-jre-jammy

# Install Node.js (LTS), git via NodeSource
RUN apt-get update && apt-get install -y curl ca-certificates gnupg git --no-install-recommends \
    && curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - \
    && apt-get install -y nodejs --no-install-recommends \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Install Claude Code CLI globally
RUN npm install -g @anthropic-ai/claude-code

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar
COPY entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

EXPOSE 8001

ENTRYPOINT ["/app/entrypoint.sh"]
