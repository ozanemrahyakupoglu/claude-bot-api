#!/bin/sh
set -e

mkdir -p /app/workspace

if [ -n "$ROLE_REPO_URL" ]; then
  echo "Cloning role repository: $ROLE_REPO_URL"
  cd /app && git clone "$ROLE_REPO_URL"

  REPO_NAME=$(basename "$ROLE_REPO_URL" .git)
  echo "Role repository cloned to /app/${REPO_NAME}"

  if [ -n "$AGENT_ROLE_PATH" ]; then
    MANIFEST_PATH="/app/${REPO_NAME}/${AGENT_ROLE_PATH}/manifest.md"
    MCP_SOURCE="/app/${REPO_NAME}/${AGENT_ROLE_PATH}/mcp.json"
    echo "Using agent path: ${AGENT_ROLE_PATH}"
  else
    MANIFEST_PATH="/app/${REPO_NAME}/manifest.md"
    MCP_SOURCE="/app/${REPO_NAME}/mcp.json"
  fi

  # MCP config varsa /app/.mcp.json olarak kopyala
  if [ -f "$MCP_SOURCE" ]; then
    cp "$MCP_SOURCE" /app/.mcp.json
    echo "MCP config copied from ${MCP_SOURCE} to /app/.mcp.json"
  else
    echo "No mcp.json found at ${MCP_SOURCE}, skipping MCP setup"
  fi

  cat > /app/CLAUDE.md <<EOF
${MANIFEST_PATH} dosyasını oku ve orada tanımlanan tüm talimatlara uy.

Sistem genelindeki tüm dosyaları okuyabilirsin. Ancak yalnızca /app/workspace/ dizini altındaki dosyaları oluşturabilir, düzenleyebilir veya silebilirsin. Bu dizin dışındaki hiçbir dosyayı değiştirme.

Eğer /app/workspace/ dışında bir dosyayı değiştirmen gerekirse; işlemi gerçekleştirme. Bunun yerine şu formatta hata döndür: "YETKİ HATASI: '<dosya_yolu>' dosyası üzerinde değişiklik yapma yetkiniz bulunmamaktadır. Yalnızca /app/workspace/ dizini altındaki dosyalar düzenlenebilir."
EOF
  echo "CLAUDE.md created pointing to ${MANIFEST_PATH}"
else
  echo "ROLE_REPO_URL not set, skipping clone"
  touch /app/CLAUDE.md
fi

exec java -jar /app/app.jar