#!/bin/bash
# bash ile çalışır: dash heredoc içindeki çok baytlı karakterleri bozuyor (ör. "ğ" -> 0xC4 baytı düşüyor)
# Agent reposunu çeker ve Claude için hazırlar. Container açılışında entrypoint.sh,
# çalışma sırasında POST /api/agent/reload çağırır. Tekrar tekrar çalıştırılabilir.
set -e

AGENT_COMMIT_FILE="/app/.agent-commit"

SKILLS_DIR="${HOME:-/home/claude-bot}/.claude/skills"

# URL içindeki token'ı loglarda gizler
mask_url() {
  printf '%s' "$1" | sed 's#://[^@/]*@#://***@#'
}

# Repo yoksa klonlar, varsa uzaktaki son hâline getirir (envsubst değişiklikleri dahil yerel her şey silinir).
# $3 verilirse yeni commit'te bu dosya yoksa mevcut dosyalara dokunmadan hata verir.
sync_repo() {
  if [ -d "$2/.git" ]; then
    echo "Updating $2 from $(mask_url "$1")"
    git -C "$2" fetch --depth 1 --quiet "$1" HEAD
    if [ -n "$3" ] && ! git -C "$2" cat-file -e "FETCH_HEAD:$3" 2>/dev/null; then
      echo "ERROR: $3 not found in latest commit, keeping current files"
      exit 1
    fi
    git -C "$2" reset --hard --quiet FETCH_HEAD
    git -C "$2" clean -fdq
  else
    rm -rf "$2"
    echo "Cloning $(mask_url "$1") into $2"
    git clone --depth 1 --quiet "$1" "$2"
  fi
}

# Yalnızca tanımlı env değişkenlerini çözer; tanımsız $ifadeler (regex, template) olduğu gibi kalır
resolve_env() {
  vars=$(awk 'BEGIN { for (k in ENVIRON) if (k ~ /^[A-Za-z_][A-Za-z0-9_]*$/) printf "${%s} ", k }')
  envsubst "$vars" < "$1" > "$1.tmp" && mv "$1.tmp" "$1"
}

# <kaynak>/<skill>/SKILL.md yapısındaki skill'leri SKILLS_DIR altına kopyalar; aynı ad varsa üzerine yazar
install_skills() {
  [ -d "$1" ] || return 0
  for skill_md in "$1"/*/SKILL.md; do
    [ -f "$skill_md" ] || continue
    skill_dir=$(dirname "$skill_md")
    skill_name=$(basename "$skill_dir")
    if [ -d "${SKILLS_DIR}/${skill_name}" ]; then
      echo "Skill '${skill_name}' overridden by $2"
      rm -rf "${SKILLS_DIR:?}/${skill_name}"
    fi
    cp -R "$skill_dir" "${SKILLS_DIR}/${skill_name}"
    echo "Skill installed ($2): ${skill_name}"
  done
}

mkdir -p /app/workspace

# AI_AGENT_CONFIGS_GIT_URL: eski ad, geriye uyumluluk için
AI_AGENT_GIT_URL="${AI_AGENT_GIT_URL:-$AI_AGENT_CONFIGS_GIT_URL}"
if [ -z "$AI_AGENT_GIT_URL" ]; then
  echo "ERROR: Required environment variable AI_AGENT_GIT_URL is not set"
  exit 1
fi

AI_AGENT_REPO_PATH="/app/$(basename "$AI_AGENT_GIT_URL" .git)"
sync_repo "$AI_AGENT_GIT_URL" "$AI_AGENT_REPO_PATH" "${AI_AGENT_ROLE:+${AI_AGENT_ROLE}/}manifest.md"

# Yeni yapıda agent dosyaları repo kökündedir; eski yapıda AI_AGENT_ROLE alt klasörü
if [ -n "$AI_AGENT_ROLE" ]; then
  AI_AGENT_SOURCE_PATH="${AI_AGENT_REPO_PATH}/${AI_AGENT_ROLE}"
else
  AI_AGENT_SOURCE_PATH="$AI_AGENT_REPO_PATH"
fi

if [ ! -f "${AI_AGENT_SOURCE_PATH}/manifest.md" ]; then
  echo "ERROR: manifest.md not found in ${AI_AGENT_SOURCE_PATH}"
  exit 1
fi

# Prompt'lar repo adından bağımsız olarak /app/agent/... yolunu kullanabilsin
AI_AGENT_PATH="/app/agent"
ln -sfn "$AI_AGENT_SOURCE_PATH" "$AI_AGENT_PATH"
echo "Agent folder: ${AI_AGENT_SOURCE_PATH} (available as ${AI_AGENT_PATH})"

MANIFEST_PATH="${AI_AGENT_PATH}/manifest.md"
MCP_PATH="${AI_AGENT_PATH}/mcp.json"

# Yalnızca prompt ve config dosyalarındaki env değişkenlerini çöz (skills/, scripts/, docker-compose.yaml hariç)
echo "Resolving env variables in manifest.md, mcp.json and system_prompts/"
for file in "${AI_AGENT_SOURCE_PATH}/manifest.md" "${AI_AGENT_SOURCE_PATH}/mcp.json"; do
  if [ -f "$file" ]; then
    resolve_env "$file"
  fi
done
if [ -d "${AI_AGENT_SOURCE_PATH}/system_prompts" ]; then
  find "${AI_AGENT_SOURCE_PATH}/system_prompts" -type f | while read -r file; do
    resolve_env "$file"
  done
fi

# MCP config varsa /app/.mcp.json olarak kopyala
if [ -f "$MCP_PATH" ]; then
  cp "$MCP_PATH" /app/.mcp.json
  echo "MCP config copied from ${MCP_PATH}"
else
  echo "No mcp.json found at ${MCP_PATH}, skipping MCP setup"
fi

# Skill'ler: önce ortak repo, sonra agent klasörü (aynı adlı skill'de agent kazanır)
rm -rf "$SKILLS_DIR"
mkdir -p "$SKILLS_DIR"

if [ -n "$AI_AGENT_SHARED_SKILLS_GIT_URL" ]; then
  SHARED_SKILLS_REPO_PATH="/app/shared-skills"
  sync_repo "$AI_AGENT_SHARED_SKILLS_GIT_URL" "$SHARED_SKILLS_REPO_PATH"
  if [ -d "${SHARED_SKILLS_REPO_PATH}/skills" ]; then
    install_skills "${SHARED_SKILLS_REPO_PATH}/skills" "shared"
  else
    install_skills "$SHARED_SKILLS_REPO_PATH" "shared"
  fi
fi

install_skills "${AI_AGENT_SOURCE_PATH}/skills" "agent"
echo "Skills ready in ${SKILLS_DIR}: $(ls "$SKILLS_DIR" | wc -l | tr -d ' ') skill(s)"

# Claude'un yazabileceği klasörler: /app/workspace ve AI_AGENT_WRITABLE_DIRS (boşlukla ayrılmış, ör. ortak volume'lar)
WRITABLE_DIRS="/app/workspace"
for dir in $AI_AGENT_WRITABLE_DIRS; do
  dir="${dir%/}"
  if [ ! -d "$dir" ] || [ ! -w "$dir" ]; then
    echo "WARNING: Writable dir ${dir} is missing or not writable by $(id -un) (uid $(id -u)); check the volume and host folder owner"
  fi
  WRITABLE_DIRS="${WRITABLE_DIRS} ${dir}"
done
WRITABLE_LIST=$(for dir in $WRITABLE_DIRS; do printf -- '- %s/\n' "$dir"; done)
WRITABLE_INLINE=$(for dir in $WRITABLE_DIRS; do printf '%s/ ' "$dir"; done | sed 's/ $//; s/ /, /g')

cat > /app/CLAUDE.md <<EOF
${MANIFEST_PATH} dosyasını oku ve orada tanımlanan tüm talimatlara uy.

Sistem genelindeki tüm dosyaları okuyabilirsin. Ancak yalnızca aşağıdaki dizinler altındaki dosyaları oluşturabilir, düzenleyebilir veya silebilirsin. Bu dizinler dışındaki hiçbir dosyayı değiştirme.

${WRITABLE_LIST}

Eğer bu dizinler dışında bir dosyayı değiştirmen gerekirse; işlemi gerçekleştirme. Bunun yerine şu formatta hata döndür: "YETKİ HATASI: '<dosya_yolu>' dosyası üzerinde değişiklik yapma yetkiniz bulunmamaktadır. Yalnızca şu dizinler altındaki dosyalar düzenlenebilir: ${WRITABLE_INLINE}"
EOF
echo "CLAUDE.md created pointing to ${MANIFEST_PATH}, writable: ${WRITABLE_INLINE}"

git -C "$AI_AGENT_REPO_PATH" rev-parse HEAD > "$AGENT_COMMIT_FILE"
echo "Agent ready at commit $(cat "$AGENT_COMMIT_FILE")"
