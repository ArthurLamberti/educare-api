# Hostinger DNS API helpers for certbot manual hooks (DNS-01).
# Token: /root/.secrets/hostinger.env with HOSTINGER_API_TOKEN=... (hPanel -> Perfil -> API), chmod 600.
set -eu
. /root/.secrets/hostinger.env
API="https://developers.hostinger.com/api/dns/v1/zones"
ZONE="arthurlamberti.com"
# _acme-challenge name relative to the zone, e.g. "_acme-challenge.educare"
SUB="${CERTBOT_DOMAIN%.$ZONE}"
[ "$SUB" = "$CERTBOT_DOMAIN" ] && { echo "domain $CERTBOT_DOMAIN outside $ZONE" >&2; exit 1; }
NAME="_acme-challenge.$SUB"
api() { curl -fsS -X "$1" "$API/$ZONE" -H "Authorization: Bearer $HOSTINGER_API_TOKEN" -H "Content-Type: application/json" -d "$2"; }
