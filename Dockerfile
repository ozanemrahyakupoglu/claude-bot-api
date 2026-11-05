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

# Install Node.js (LTS), git, gettext-base (for envsubst), python3 (for skill/role scripts) via NodeSource
RUN apt-get update && apt-get install -y curl ca-certificates gnupg git gettext-base python3 --no-install-recommends \
    && curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - \
    && apt-get install -y nodejs --no-install-recommends \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Install Claude Code CLI globally (pin with --build-arg CLAUDE_CODE_VERSION=x.y.z)
ARG CLAUDE_CODE_VERSION=latest
RUN npm install -g @anthropic-ai/claude-code@${CLAUDE_CODE_VERSION}

RUN useradd -m -u 1000 claude-bot \
    && mkdir -p /home/claude-bot/.claude/skills \
    && chown -R claude-bot:claude-bot /home/claude-bot/.claude

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar
COPY entrypoint.sh setup-agent.sh ./
RUN chmod +x entrypoint.sh setup-agent.sh \
    && mkdir -p /app/workspace \
    && chown -R claude-bot:claude-bot /app

USER claude-bot

# Claude Code tüm ayar ve girişini (.claude.json dahil) tek klasörde tutsun; kalıcı yapmak için bkz. entrypoint.sh
ENV CLAUDE_CONFIG_DIR=/home/claude-bot/.claude

EXPOSE 8001

ENTRYPOINT ["/app/entrypoint.sh"]