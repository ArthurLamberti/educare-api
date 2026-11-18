# Deploying Educare on a VPS

Postgres, the API and the web run with Docker Compose. Only the web is published, on `127.0.0.1:3100`;
the host's nginx serves HTTPS for `educare.arthurlamberti.com` and every school subdomain
(`<school>.educare.arthurlamberti.com`) and proxies to it. The API is reachable only inside Docker
(the web calls it from its server). LiveKit and SMTP are external services.

## 1. DNS

Point both names to the VPS (A record, plus AAAA if it has IPv6):

| Name | Type | Value |
|---|---|---|
| `educare.arthurlamberti.com` | A | VPS IP |
| `*.educare.arthurlamberti.com` | A | VPS IP |

## 2. Code

Clone both repos side by side (the compose file builds `../` and `../../educare-web`):

```bash
mkdir -p /opt/educare && cd /opt/educare
git clone git@github.com:ArthurLamberti/educare-api.git
git clone git@github.com:ArthurLamberti/educare-web.git
```

## 3. Configuration

```bash
cd /opt/educare/educare-api/deploy
cp .env.example .env && chmod 600 .env
```

Fill in `.env`. Generate secrets with `openssl rand -base64 48` (`POSTGRES_PASSWORD`, `EDUCARE_JWT_SECRET`,
`EDUCARE_PLATFORM_KEY`). SMTP is required: sign-up confirmation and password resets are sent by email.
`.env` is ignored by git.

## 4. HTTPS certificate

Schools are subdomains, so the certificate must cover the wildcard, which Let's Encrypt only issues
through a DNS challenge. With a certbot plugin for your DNS provider (renews automatically), e.g. Cloudflare:

```bash
sudo apt install python3-certbot-dns-cloudflare
# /root/.secrets/cloudflare.ini: dns_cloudflare_api_token = <token with Zone:DNS:Edit>
sudo chmod 600 /root/.secrets/cloudflare.ini
sudo certbot certonly --dns-cloudflare --dns-cloudflare-credentials /root/.secrets/cloudflare.ini \
  -d educare.arthurlamberti.com -d '*.educare.arthurlamberti.com'
```

Without a plugin, `--manual --preferred-challenges dns` works too, but then each renewal (every 90 days)
asks for a new TXT record by hand.

## 5. nginx

```bash
sudo cp /opt/educare/educare-api/deploy/nginx/educare.conf /etc/nginx/sites-available/educare.conf
sudo ln -s /etc/nginx/sites-available/educare.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

If `WEB_PORT` in `.env` is not 3100, change `proxy_pass` to match.

## 6. Start

```bash
cd /opt/educare/educare-api/deploy
docker compose --env-file .env up -d --build
docker compose logs -f api   # "Started EducareApiApplication" and the migrations applied
```

Then open `https://educare.arthurlamberti.com/admin/login`, use "Esqueci a senha (ou primeiro acesso)"
with `EDUCARE_ADMIN_EMAIL` to set the system admin's password, and create schools or activate spaces there.

## Updates

```bash
cd /opt/educare/educare-api && git pull
cd /opt/educare/educare-web && git pull
cd /opt/educare/educare-api/deploy && docker compose --env-file .env up -d --build
```

Database migrations run when the API starts.

## Backups

```bash
docker compose --env-file .env exec -T postgres pg_dump -U educare educare | gzip > educare-$(date +%F).sql.gz
# restore into an empty database:
gunzip -c educare-YYYY-MM-DD.sql.gz | docker compose --env-file .env exec -T postgres psql -U educare educare
```

The data lives in the `educare_postgres-data` Docker volume; schedule the dump (cron) and copy it off the VPS.
