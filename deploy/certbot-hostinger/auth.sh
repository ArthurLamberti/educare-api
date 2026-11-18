#!/bin/sh
# certbot --manual-auth-hook: adds the TXT value (appends, so the base and wildcard values coexist)
# and waits until Hostinger nameservers serve it.
. /etc/letsencrypt/hostinger/common.sh
api PUT "{\"overwrite\":false,\"zone\":[{\"name\":\"$NAME\",\"type\":\"TXT\",\"ttl\":60,\"records\":[{\"content\":\"$CERTBOT_VALIDATION\"}]}]}" >/dev/null
for i in $(seq 1 60); do
  if dig +short TXT "$NAME.$ZONE" @ns1.dns-parking.com | grep -q -- "$CERTBOT_VALIDATION"; then sleep 20; exit 0; fi
  sleep 10
done
echo "TXT $NAME.$ZONE not visible after 10 min" >&2; exit 1
