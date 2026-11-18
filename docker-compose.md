# Docker Compose - Educare API

## Local (`docker-compose.local.yml`)

PostgreSQL and Mailpit for development. `./gradlew bootRun` starts them automatically (Spring Boot Docker
Compose support, profile `dev`); by hand:

```bash
docker compose -f docker-compose.local.yml up -d
```

Emails sent in development appear at http://localhost:8026.

## VPS (`docker-compose.yml`)

PostgreSQL and the API (profile `prod`, image from GHCR). The web (`educare-web`, `docker-compose.prod.yml`)
joins the external network `educare-net` and calls the API by container name (`http://educare-api:8080`), never
through the internet or nginx. Every port is bound to `127.0.0.1`; the host's nginx serves HTTPS for
`educare.arthurlamberti.com` and the school subdomains and proxies to the web.

| Container | Port on the VPS (127.0.0.1) |
|---|---|
| `educare-postgres` | `DATABASE_PORT`, default 5436 (SSH tunnel) |
| `educare-api` | `SERVER_PORT`, default 8082 (SSH tunnel; the web uses the network) |
| `educare-web` | `WEB_PORT`, default 3002 (nginx) |

```bash
docker compose --env-file .env up -d
docker inspect --format '{{.State.Health.Status}}' educare-api   # should reach "healthy"
```

### Variables

See `.env.example`. Required: `DATABASE_PASSWORD`, `EDUCARE_JWT_SECRET` (32+ bytes), `EDUCARE_PLATFORM_KEY`,
`EDUCARE_ADMIN_EMAIL`, `ROOT_DOMAIN`, `SMTP_HOST` and `MAIL_FROM`. Optional: `SMTP_PORT` (587), `SMTP_USERNAME`,
`SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS` (`true`), `MAIL_OPERATOR`, `LIVEKIT_URL`, `LIVEKIT_API_KEY`,
`LIVEKIT_API_SECRET`, `DATABASE_NAME`, `DATABASE_USERNAME`, `DATABASE_PORT`, `SERVER_PORT`,
`SPRING_PROFILES_ACTIVE` (`prod`). Generate secrets with `openssl rand -base64 48`.

### Health check

The container uses `GET /actuator/health/readiness`, which includes the database. Liveness:
`/actuator/health/liveness`. SMTP is left out, so a mail outage does not make the API unhealthy.

## Deploy to the VPS (pipeline)

`.github/workflows/ci.yml` runs on every push and pull request:

1. `build`: Gradle build and tests.
2. `docker` (push to `main` or `staging`): publishes `ghcr.io/arthurlamberti/educare-api` with `latest`
   (main) or `staging`, plus `<commit sha>`.
3. `deploy` (push to `staging` only): connects to the VPS over SSH, runs `docker compose pull api` and
   `docker compose up -d --no-build --no-deps api` with `API_IMAGE_TAG=<sha>` and waits for `healthy`.

Versions: `.github/workflows/release.yml` (release-please) keeps a release PR up to date from the
`feat:`/`fix:` commits on `main`; merging it creates the tag `vX.Y.Z`, the GitHub Release, the `CHANGELOG.md`
and the image `ghcr.io/arthurlamberti/educare-api:X.Y.Z`. Do not create tags by hand.

### GitHub settings (Settings → Secrets and variables → Actions, environment `staging`)

| Name | Type | Value |
|---|---|---|
| `VPS_HOST` | secret | VPS IP or host |
| `VPS_USER` | secret | SSH user (must be able to run `docker`) |
| `VPS_SSH_KEY` | secret | private SSH key whose public key is in the VPS `authorized_keys` |
| `VPS_PORT` | secret (optional) | SSH port, default `22` |
| `VPS_APP_DIR` | variable (optional) | folder with `docker-compose.yml` and `.env`, default `/opt/educare-api` |

The workflow logs in to GHCR with its own temporary token, so the VPS needs no fixed credential. For a manual
pull of the private package, log in first with a PAT that has `read:packages`.

### Preparing the VPS (once)

1. DNS (Hostinger): `educare.arthurlamberti.com` and `*.educare.arthurlamberti.com` → VPS IP (A records).
2. Network and API stack:

   ```bash
   docker network create educare-net
   mkdir -p /opt/educare-api && cd /opt/educare-api
   # copy docker-compose.yml and create .env (see .env.example), then:
   docker compose up -d postgres
   ```

   The first push to `staging` deploys the API (or pull `latest` by hand: `docker compose up -d api`).
3. Web stack: see `educare-web` (`docker-compose.prod.yml`, folder `/opt/educare-web`).
4. HTTPS certificate. Schools are subdomains, so it must cover the wildcard, which Let's Encrypt only issues
   through a DNS challenge. The DNS is at Hostinger: `deploy/certbot-hostinger/` has certbot hooks that add and
   remove the `_acme-challenge` TXT records through the Hostinger DNS API, so renewals are automatic.

   ```bash
   # API token from hPanel -> Profile -> API
   sudo install -d -m 700 /root/.secrets
   sudo sh -c 'umask 077; echo "HOSTINGER_API_TOKEN=<token>" > /root/.secrets/hostinger.env'
   sudo install -d /etc/letsencrypt/hostinger
   sudo install -m 700 deploy/certbot-hostinger/*.sh /etc/letsencrypt/hostinger/
   sudo certbot certonly --manual --preferred-challenges dns \
     --manual-auth-hook /etc/letsencrypt/hostinger/auth.sh \
     --manual-cleanup-hook /etc/letsencrypt/hostinger/cleanup.sh \
     --deploy-hook "systemctl reload nginx" \
     -d educare.arthurlamberti.com -d '*.educare.arthurlamberti.com'
   ```

   DNS records: `educare` and `*.educare` A → VPS IP.

5. nginx: copy `deploy/nginx/educare.conf` to `/etc/nginx/sites-available/`, link it in `sites-enabled/`,
   then `sudo nginx -t && sudo systemctl reload nginx`.
6. System admin: open `https://educare.arthurlamberti.com/admin/login`, use "Esqueci a senha (ou primeiro
   acesso)" with `EDUCARE_ADMIN_EMAIL`.

### Rollback

```bash
cd /opt/educare-api
API_IMAGE_TAG=<previous sha> docker compose up -d --no-build --no-deps api
```

### Database

```bash
ssh -L 5436:127.0.0.1:5436 <user>@<vps>   # then connect to localhost:5436
# backup on the VPS:
docker exec educare-postgres pg_dump -U educare educare | gzip > educare-$(date +%F).sql.gz
```
