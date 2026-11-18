#!/bin/sh
# certbot --manual-cleanup-hook: removes the challenge TXT records.
. /etc/letsencrypt/hostinger/common.sh
api DELETE "{\"filters\":[{\"name\":\"$NAME\",\"type\":\"TXT\"}]}" >/dev/null || true
