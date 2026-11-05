# claude-bot-api

Claude Code CLI'yi HTTP API olarak sunan Spring Boot (Java 21) uygulaması. Agent'lar bu image ile çalışır. Her container bir agent reposunu klonlar ve onu yükler.

## Agent reposu yapısı

Her agent tek bir repodur. Tüm dosyalar repo kökündedir ([example-agent](../example-agent) şablonunu kopyalayarak başla):

```
example-agent/
├── manifest.md           # Giriş noktası; Claude ilk bunu okur
├── mcp.json              # MCP server tanımları
├── docker-compose.yaml   # Agent'ın deployment'ı
├── system_prompts/       # Her istekte yüklenen talimatlar (01-role.md, 02-workflow.md, ...)
├── skills/               # Gerektiğinde yüklenen skill'ler (<ad>/SKILL.md)
└── scripts/              # Opsiyonel; yalnızca gerçekten gerekiyorsa
```

Agent klasörü container içinde her zaman `/app/agent/` yolundadır. Prompt'larda dosyalara bu yolla eriş, ör. `python3 /app/agent/scripts/output.py`.

Eski yapı da desteklenir: repo içinde bir alt klasör (ör. `instagram-reels-scraper-agent/instagram-reels-scraper/`) varsa `AI_AGENT_ROLE` ile o klasörün adı verilir.

## Nasıl çalışır

Container açılışında `entrypoint.sh`, `setup-agent.sh` script'ini çalıştırır. Bu script şunları yapar:

1. `AI_AGENT_GIT_URL` reposunu `/app/<repo-adı>` altına klonlar. Repo zaten varsa (restart, reload) son commit'e getirir. Yerel değişiklikler silinir.
2. Agent klasörünü (repo kökü veya `AI_AGENT_ROLE` alt klasörü) `/app/agent` olarak bağlar. `manifest.md` yoksa açılmaz.
3. `manifest.md`, `mcp.json` ve `system_prompts/` altındaki dosyalarda env değişkenlerini `envsubst` ile çözer. Yalnızca container'da tanımlı değişkenler çözülür, tanımsız `$ifadeler` olduğu gibi kalır. `skills/`, `scripts/` ve `docker-compose.yaml` dosyalarına dokunulmaz.
4. `mcp.json` dosyasını `/app/.mcp.json` olarak kopyalar.
5. Skill'leri `~/.claude/skills/` altına kurar (bkz. [Skill'ler](#skilller)).
6. `/app/CLAUDE.md` oluşturur. Bu dosya Claude'u `/app/agent/manifest.md` dosyasına yönlendirir ve yazma iznini `/app/workspace` ile `AI_AGENT_WRITABLE_DIRS` klasörlerine sınırlar.
Ardından API `8001` portunda başlar. Restart'ta güncelleme başarısız olursa (GitHub'a erişilemiyor, bozuk commit) container diskteki son çalışan kopyayla açılır.

## Restart'sız güncelleme

`docker-compose.yaml` dışındaki agent dosyalarında (manifest, prompt'lar, skill'ler, script'ler, `mcp.json`) yapılan değişiklikler container restart edilmeden alınabilir. Değişikliği push'ladıktan sonra:

```sh
curl -X POST http://localhost:8001/api/agent/reload
```

```json
{
  "previousCommit": "8f8c1bc…",
  "commit": "49ca872…",
  "changed": true,
  "closedSessions": 1,
  "skills": ["example-skill", "new-skill"],
  "durationMs": 650
}
```

- `setup-agent.sh` yeniden çalışır: repo güncellenir, env değişkenleri çözülür, `mcp.json` ve skill'ler yeniden kurulur. Claude CLI her istekte yeni süreç olarak başladığından bir sonraki istek güncel dosyalarla çalışır.
- Açık oturumlar kapatılır. Bu oturumlar eski talimatları context'lerinde taşır.
- Çalışan bir istek varken reload `409` döner. Reload sürerken gelen istekler bitmesini bekler.
- Yeni commit'te `manifest.md` yoksa veya repo çekilemezse `500` döner ve mevcut dosyalara dokunulmaz.
- Çalışan commit'i görmek için: `GET /api/agent`.
- Env değişkenleri compose'tan gelir; onları değiştirmek için restart gerekir.

## Skill'ler

Skill'ler iki kaynaktan gelir ve `/home/claude-bot/.claude/skills/` altına kopyalanır. Claude Code bunları otomatik keşfeder ve gerektiğinde yükler. Skill içeriği system prompt'lar gibi her istekte context'e girmez, yalnızca kullanıldığında yüklenir.

1. **Ortak repo** (opsiyonel): `AI_AGENT_SHARED_SKILLS_GIT_URL`. Repoda `skills/` klasörü varsa skill'ler oradan, yoksa repo kökünden okunur. Tüm agent'lar bu repoyu kullanabilir.
2. **Agent klasörü**: `skills/`. Aynı adlı bir skill ortak repoda da varsa agent'taki geçerli olur.

Her skill kendi klasöründe durur ve bir `SKILL.md` dosyası içerir:

```
skills/
└── comment-collector/
    ├── SKILL.md          # frontmatter: name, description
    └── scripts/          # opsiyonel; skill'in kullandığı kod
```

```markdown
---
name: comment-collector
description: Yorumları ve yanıtları toplar. Gönderi yorumları istendiğinde kullan.
---

Talimatlar...
```

Claude, skill'i ne zaman kullanacağına `description` alanına bakarak karar verir. Bu yüzden açıklama, skill'in ne yaptığını ve hangi durumda kullanılacağını söylemeli.

## Arayüz

`http://localhost:8001/` adresinde agent'ı kullanmak için basit bir ekran var:

- **Oturumlar:** Sol panelde açık oturumlar listelenir. Yeni oturum açılabilir, silinebilir. Seçili oturum URL'de tutulur (`#s=<id>`); sayfa yenilense de açık kalır.
- **Prompt:** Seçili oturuma (veya tek seferlik olarak) prompt gönderilir. Ctrl/⌘ + Enter ile gönderilir.
- **Adımlar:** Her mesajın altında Claude'un adımları görünür: başlangıç bilgisi (model, MCP durumları), yazdığı metin, çağırdığı araçlar ve skill'ler, araç sonuçları. Çalışırken canlı akar.
- **İptal:** Çalışan bir mesaj iptal edilebilir. CLI ve başlattığı tüm süreçler kapatılır.
- **Canlı log:** Alttaki panelde tüm isteklerin adımları akar, n8n'den gelenler dahil.
- **Reload ve skill'ler:** Üst bardan agent reload edilir ve yüklü skill'ler görülür. Çalışan commit ve çalışan istek sayısı da burada görünür.

Geçmiş bellekte tutulur. Container restart'ında, reload'da ve oturum silindiğinde gider. Son 100 çalıştırma ve her biri için son 1000 adım saklanır; uzun araç çıktıları (ör. sayfa snapshot'ları) 2000 karakterde kesilir.

Arayüz ve API, `API_AUTH_USERNAME` ve `API_AUTH_PASSWORD` verilirse HTTP Basic Auth ile korunur. Tarayıcı kullanıcı adı ve şifreyi kendisi sorar. n8n gibi istemcilerin de aynı bilgilerle Basic Auth göndermesi gerekir. Port yine yalnızca `127.0.0.1` üzerinden açılmalı.

## Kalıcı klasörler ve Claude girişi

Önerilen yapı (bkz. [instagram-reels-scraper-agent-v2](../instagram-reels-scraper-agent-v2/docker-compose.yaml)). Kural: aynı compose'taki container'lar (agent ve browser'lar) **private** üzerinden, farklı compose'lar (n8n vb.) **public** üzerinden dosya paylaşır.

```
container                          host
/app/workspace/                    container içinde, kalıcı değil
├── private/                  ←→  /home/bim/data/private/<agent>         okuma + yazma
│   ├── claude/                    Claude girişi ve ayarları
│   └── browser-vnc/ ...           aynı compose'taki browser'ların /output'u
└── public/                   ←→  /home/bim/data/public                  salt okunur (tüm agent'ların paylaşımları)
    └── <agent>/              ←→  /home/bim/data/public/<agent>          okuma + yazma (ör. agent-output/, n8n okur)
```

```yaml
    environment:
      AI_AGENT_PRIVATE_DIR: /app/workspace/private
    volumes:
      - /home/bim/data/private/example-agent:/app/workspace/private
      - /home/bim/data/public:/app/workspace/public:ro
      - /home/bim/data/public/example-agent:/app/workspace/public/example-agent
```

Claude Code girişini ve ayarlarını `CLAUDE_CONFIG_DIR` (`/home/claude-bot/.claude`) altında tutar. `AI_AGENT_PRIVATE_DIR` verilirse entrypoint bunu `<AI_AGENT_PRIVATE_DIR>/claude` klasörüne yönlendirir; giriş restart, container'ın yeniden oluşturulması ve imaj güncellemesinde korunur. Bir kez giriş yapın:

```sh
docker exec -it <container> claude    # içeride /login
```

Host klasörleri önceden oluşturulmalı ve sahibi container kullanıcısı (uid 1000) olmalı; yoksa Docker onları root sahipliğinde açar ve agent yazamaz. Yazılamazsa açılışta uyarı verilir.

## Ortam değişkenleri

| Değişken | Zorunlu | Açıklama |
|---|---|---|
| `AI_AGENT_GIT_URL` | Evet | Agent reposunun git URL'i. Private repo için token içerir. Loglarda token maskelenir. Eski ad `AI_AGENT_CONFIGS_GIT_URL` da kabul edilir. |
| `AI_AGENT_ROLE` | Hayır | Yalnızca eski yapı için: agent dosyalarının bulunduğu alt klasörün adı. Boşsa repo kökü kullanılır. |
| `AI_AGENT_SHARED_SKILLS_GIT_URL` | Hayır | Ortak skill reposunun git URL'i. |
| `AI_AGENT_NAME` | Hayır | Agent adı, arayüzde görünür. Boşsa `AI_AGENT_ROLE`, o da yoksa repo adı kullanılır. |
| `AI_AGENT_PRIVATE_DIR` | Hayır | Agent'a özel kalıcı klasörün container'daki yolu. Verilirse Claude girişi ve ayarları `<AI_AGENT_PRIVATE_DIR>/claude` altında tutulur (bkz. [Kalıcı klasörler](#kalıcı-klasörler-ve-claude-girişi)). |
| `AI_AGENT_WRITABLE_DIRS` | Hayır | `/app/workspace` dışında Claude'un yazabileceği klasörler, boşlukla ayrılmış (ör. ortak bir volume). Klasör yoksa veya container kullanıcısı (uid 1000) yazamıyorsa açılışta uyarı verilir. |
| `CLAUDE_CLI_TIMEOUT_SECONDS` | Hayır | CLI çağrısı zaman aşımı (varsayılan 3600). Süre dolunca CLI ve alt süreçleri öldürülür. |
| `CLAUDE_CLI_MODEL` | Hayır | `--model` olarak geçilir. Boşsa CLI varsayılanı kullanılır. |
| `CLAUDE_CLI_MAX_TURNS` | Hayır | `--max-turns` olarak geçilir. |
| `API_AUTH_USERNAME`, `API_AUTH_PASSWORD` | Hayır | İkisi de verilirse arayüz ve tüm API Basic Auth ister. Boşsa auth kapalıdır. |

## API

| Metot | Yol | Açıklama |
|---|---|---|
| `POST` | `/api/prompts` | Tek seferlik prompt. Gövde: `{"content": "...", "cwd": "..."}` |
| `POST` | `/api/sessions` | Yeni oturum aç. Gövde: `{"cwd": "..."}`. `cwd` dizini yoksa `400`. |
| `GET` | `/api/sessions` | Oturumları listele (`busy`: o anda çalışan mesaj var mı, `title`: ilk mesaj, `messageCount`) |
| `GET` | `/api/sessions/{sessionId}` | Oturum bilgisi |
| `GET` | `/api/sessions/current` | Aktif oturum |
| `POST` | `/api/sessions/{sessionId}/messages` | Oturuma mesaj gönder. Gövde: `{"content": "..."}` |
| `DELETE` | `/api/sessions/{sessionId}` | Oturumu ve geçmişini sil |
| `POST` | `/api/runs` | Prompt'u arka planda başlat, hemen `202` ile run döner. Gövde: `{"content": "...", "sessionId": "...", "cwd": "..."}` (`sessionId` yoksa tek seferlik) |
| `GET` | `/api/runs?sessionId=&limit=` | Son çalıştırmalar, yeniden eskiye. `sessionId=none`: tek seferlikler |
| `GET` | `/api/runs/{runId}` | Çalıştırma ve adımları (`events`) |
| `POST` | `/api/runs/{runId}/cancel` | Çalışan isteği iptal et |
| `GET` | `/api/events` | Tüm çalıştırmaların canlı olayları (Server-Sent Events, `event: run`) |
| `GET` | `/api/skills` | Yüklü skill'ler (`name`, `description`, `path`) |
| `GET` | `/api/agent` | Agent adı, çalışan commit, o anda çalışan istek sayısı |
| `POST` | `/api/agent/reload` | Agent dosyalarını git'ten yeniden çek ve yükle (bkz. [Restart'sız güncelleme](#restartsız-güncelleme)) |

Mesaj ve prompt yanıtı:

```json
{
  "sessionId": "…",
  "content": "Claude'un yanıtı",
  "duration": "00:03:12.481",
  "numTurns": 14,
  "costUsd": 0.4213,
  "skillsUsed": ["comment-collector"]
}
```

Hatalar:

- Aynı oturumda bir mesaj hâlâ çalışırken gelen ikinci mesaj ve silme isteği `409` döner.
- `/api/prompts` ve `/api/sessions/{id}/messages` eskisi gibi iş bitene kadar bekler ve sonucu döner; bu istekler de arayüzde ve canlı logda görünür.
- Claude hata sonucu dönerse (ör. `error_max_turns`), CLI 0 koduyla çıksa bile `500` dönülür. Mesajda hatanın türü yazar.

API'de kimlik doğrulama yok. Port dışarıya açılmamalı.

## Build

```sh
docker buildx build --platform linux/amd64,linux/arm64 -t ozanemrahyakupoglu/claude-bot-api:<versiyon> --push .
```

Claude Code sürümünü sabitlemek için `--build-arg CLAUDE_CODE_VERSION=x.y.z` verin (varsayılan `latest`).

Deploy edilen versiyon [ai-agent-deployments](../ai-agent-deployments) altındaki compose dosyalarında belirtilir.

## Ayarlar

`src/main/resources/application.yml`:

- `claude.cli.timeout-seconds`: CLI çağrısı zaman aşımı (varsayılan 3600)
- `claude.cli.default-cwd`: varsayılan çalışma dizini (`/app`)
- `claude.cli.model`, `claude.cli.max-turns`: opsiyonel CLI parametreleri
- `claude.agent.setup-timeout-seconds`: reload sırasında `setup-agent.sh` zaman aşımı (varsayılan 300)
- `claude.runs.max-runs`, `claude.runs.max-events-per-run`, `claude.runs.max-event-text-length`: arayüz için bellekte tutulan geçmişin sınırları (100, 1000, 2000)
- `claude.skills.dirs`: `GET /api/skills` için taranan klasörler (varsayılan `~/.claude/skills`)
