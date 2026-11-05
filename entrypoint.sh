#!/bin/sh
set -e

# Agent adı: AI_AGENT_NAME, yoksa eski yapıdaki rol klasörü, yoksa repo adı
if [ -z "$AI_AGENT_NAME" ]; then
  if [ -n "$AI_AGENT_ROLE" ]; then
    AI_AGENT_NAME="$AI_AGENT_ROLE"
  else
    AI_AGENT_NAME=$(basename "${AI_AGENT_GIT_URL:-$AI_AGENT_CONFIGS_GIT_URL}" .git)
  fi
fi
export AI_AGENT_NAME

# Claude girişi ve ayarları container yeniden oluşturulsa da kalsın: <AI_AGENT_PRIVATE_DIR>/claude
# AI_AGENT_PRIVATE_DIR, host'ta bu agent'a ayrılmış klasörün mount edildiği yerdir (ör. /app/workspace/private)
if [ -n "$AI_AGENT_PRIVATE_DIR" ]; then
  CLAUDE_DATA_DIR="${AI_AGENT_PRIVATE_DIR%/}/claude"
  if mkdir -p "$CLAUDE_DATA_DIR" 2>/dev/null && [ -w "$CLAUDE_DATA_DIR" ]; then
    if [ ! -L "$CLAUDE_CONFIG_DIR" ]; then
      rm -rf "$CLAUDE_CONFIG_DIR"
    fi
    ln -sfn "$CLAUDE_DATA_DIR" "$CLAUDE_CONFIG_DIR"
    echo "Claude config persisted in ${CLAUDE_DATA_DIR}"
  else
    echo "WARNING: ${CLAUDE_DATA_DIR} is not writable by uid $(id -u); Claude login will not survive container recreation"
  fi
fi

# Restart'ta güncelleme başarısız olursa (GitHub erişilemiyor, bozuk commit) diskteki son çalışan kopyayla aç
if ! /app/setup-agent.sh; then
  if [ -f /app/.agent-commit ]; then
    echo "WARNING: Agent update failed, starting with previous commit $(cat /app/.agent-commit)"
  else
    echo "ERROR: Agent setup failed"
    exit 1
  fi
fi

exec java -jar /app/app.jar
