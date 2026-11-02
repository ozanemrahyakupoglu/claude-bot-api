# claude-bot-api

Claude Code CLI'yi HTTP API olarak sunan Spring Boot (Java 21) uygulaması. Agent'lar bu image ile çalışır. Her container, [ai-agent-configs](../ai-agent-configs) reposundaki bir rolü yükler.

## Nasıl çalışır

Container açılışında `entrypoint.sh` şunları yapar:

1. `AI_AGENT_CONFIGS_GIT_URL` reposunu `/app` altına klonlar.
2. `AI_AGENT_ROLE` klasöründeki tüm dosyalarda env değişkenlerini `envsubst` ile çözer.
3. Rolün `mcp.json` dosyasını `/app/.mcp.json` olarak kopyalar.
4. `/app/CLAUDE.md` oluşturur. Bu dosya Claude'u rolün `manifest.md` dosyasına yönlendirir ve yazma iznini `/app/workspace` ile sınırlar.
5. API'yi `8001` portunda başlatır.

## Ortam değişkenleri

| Değişken | Zorunlu | Açıklama |
|---|---|---|
| `AI_AGENT_CONFIGS_GIT_URL` | Evet | Rol reposunun git URL'i. Private repo için token içerir. |
| `AI_AGENT_ROLE` | Evet | Repodaki rol klasörünün adı (ör. `instagram-reels-scraper`). |

## API

| Metot | Yol | Açıklama |
|---|---|---|
| `POST` | `/api/prompts` | Tek seferlik prompt. Gövde: `{"content": "...", "cwd": "..."}` |
| `POST` | `/api/sessions` | Yeni oturum aç. Gövde: `{"cwd": "..."}` |
| `GET` | `/api/sessions` | Oturumları listele |
| `GET` | `/api/sessions/current` | Aktif oturum |
| `POST` | `/api/sessions/{sessionId}/messages` | Oturuma mesaj gönder. Gövde: `{"content": "..."}` |
| `DELETE` | `/api/sessions/{sessionId}` | Oturumu kapat |

API'de kimlik doğrulama yok. Port dışarıya açılmamalı.

## Build

```sh
docker build -t ozanemrahyakupoglu/claude-bot-api:<versiyon> .
docker push ozanemrahyakupoglu/claude-bot-api:<versiyon>
```

Deploy edilen versiyon [ai-agent-deployments](../ai-agent-deployments) altındaki compose dosyalarında belirtilir.

## Ayarlar

`src/main/resources/application.yml`:

- `claude.cli.timeout-seconds`: CLI çağrısı zaman aşımı (varsayılan 120)
- `claude.cli.default-cwd`: varsayılan çalışma dizini (`/app`)
