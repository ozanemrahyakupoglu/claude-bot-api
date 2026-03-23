#!/bin/sh
set -e

mkdir -p /app/workspace

if [ -z "$AI_AGENT_CONFIGS_GIT_URL" ]; then
  echo "ERROR: Required environment variable AI_AGENT_CONFIGS_GIT_URL is not set"
  exit 1
fi

if [ -z "$AI_AGENT_ROLE" ]; then
  echo "ERROR: Required environment variable AI_AGENT_ROLE is not set"
  exit 1
fi

echo "Cloning role repository: $AI_AGENT_CONFIGS_GIT_URL"
cd /app && git clone "$AI_AGENT_CONFIGS_GIT_URL"

AI_AGENT_CONFIGS_PATH="/app/$(basename "$AI_AGENT_CONFIGS_GIT_URL" .git)"
echo "Role repository cloned to ${AI_AGENT_CONFIGS_PATH}"

AI_AGENT_ROLE_PATH="${AI_AGENT_CONFIGS_PATH}/${AI_AGENT_ROLE}"
MANIFEST_PATH="${AI_AGENT_ROLE_PATH}/manifest.md"
MCP_PATH="${AI_AGENT_ROLE_PATH}/mcp.json"
echo "Using agent role: ${AI_AGENT_ROLE}"

# Tüm dosyalardaki env variable'ları resolve et
echo "Resolving env variables in all files under ${AI_AGENT_ROLE_PATH}"
find "${AI_AGENT_ROLE_PATH}" -type f | while read -r file; do
  envsubst < "$file" > "$file.tmp" && mv "$file.tmp" "$file"
done

# MCP config varsa /app/.mcp.json olarak kopyala
if [ -f "$MCP_PATH" ]; then
  cp "$MCP_PATH" /app/.mcp.json
  echo "MCP config copied from ${MCP_PATH}"
else
  echo "No mcp.json found at ${MCP_PATH}, skipping MCP setup"
fi

cat > /app/CLAUDE.md <<EOF
${MANIFEST_PATH} dosyasını oku ve orada tanımlanan tüm talimatlara uy.

Sistem genelindeki tüm dosyaları okuyabilirsin. Ancak yalnızca /app/workspace/ dizini altındaki dosyaları oluşturabilir, düzenleyebilir veya silebilirsin. Bu dizin dışındaki hiçbir dosyayı değiştirme.

Eğer /app/workspace/ dışında bir dosyayı değiştirmen gerekirse; işlemi gerçekleştirme. Bunun yerine şu formatta hata döndür: "YETKİ HATASI: '<dosya_yolu>' dosyası üzerinde değişiklik yapma yetkiniz bulunmamaktadır. Yalnızca /app/workspace/ dizini altındaki dosyalar düzenlenebilir."
EOF
echo "CLAUDE.md created pointing to ${MANIFEST_PATH}"

exec java -jar /app/app.jar