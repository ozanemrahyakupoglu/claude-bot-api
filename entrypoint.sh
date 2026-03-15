#!/bin/sh
set -e

mkdir -p /app/workspace

if [ -n "$ROLE_REPO_URL" ]; then
  echo "Cloning role repository: $ROLE_REPO_URL"
  cd /app && git clone "$ROLE_REPO_URL"

  REPO_NAME=$(basename "$ROLE_REPO_URL" .git)
  MANIFEST_PATH="/app/${REPO_NAME}/manifest.md"
  echo "Role repository cloned to /app/${REPO_NAME}"

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
