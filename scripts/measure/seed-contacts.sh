#!/usr/bin/env bash
# T-04a: seed SYNTHETIC contacts into an emulator/test device contacts provider.
# Every row uses account_type/account_name=measure.local so cleanup never
# touches real data. Idempotent: re-running first deletes its own set.
#
# Usage: seed-contacts.sh [-s SERIAL] COUNT   (COUNT e.g. 50, 500, 2000)
# NOTE: ~5 `content` calls per contact, each a fresh on-device process, so
# 2000 contacts takes roughly 30+ minutes.
#
# Photos, a slow/failing provider and corrupt/rotated streams are NOT seeded
# here: those need the separately-selected test-only fixture harness
# (docs/performance-baseline.md, "Pending selections").
set -euo pipefail
export LC_ALL=C   # stable numeric/sort behaviour regardless of host locale

# One seeding run per host. flock where it exists (Linux); macOS fallback.
LOCKD=""
if command -v flock > /dev/null 2>&1; then
  exec 9> /tmp/mama-baseline.lock
  flock 9
else
  LOCKD=/tmp/mama-baseline.lock.d
  mkdir "$LOCKD" 2> /dev/null || { echo "another seeding run is active ($LOCKD)" >&2; exit 1; }
fi
# Register cleanup immediately after acquiring the lock (an early usage exit
# must not strand it); SCRATCH may not exist yet.
SCRATCH=""
cleanup() { [ -z "$SCRATCH" ] || rm -rf "$SCRATCH"; [ -z "$LOCKD" ] || rmdir "$LOCKD" 2> /dev/null || true; }
trap cleanup EXIT

ADB=(adb); COUNT=""
while [ $# -gt 0 ]; do
  case "$1" in
    -s) [ $# -ge 2 ] || { echo "-s needs a value" >&2; exit 2; }; ADB+=( -s "$2" ); shift 2 ;;
    *) COUNT="$1"; shift ;;
  esac
done
case "${COUNT:-}" in ''|*[!0-9]*) echo "usage: seed-contacts.sh [-s SERIAL] COUNT (positive integer)" >&2; exit 1 ;; esac
[ "$COUNT" -gt 0 ] || { echo "COUNT must be positive" >&2; exit 1; }

# All adb calls go through here so the lock fds (8/9) never leak into a
# freshly started adb server (it daemonizes and would hold the lock forever).
adbq() { "${ADB[@]}" "$@" 8>&- 9>&-; }

ACCT="measure.local"
SCRATCH="$(mktemp -d)"

echo "== cleaning previous synthetic set ($ACCT)"
# caller_is_syncadapter=true makes the delete REAL, not a deleted=1 tombstone.
adbq shell "content delete --uri 'content://com.android.contacts/raw_contacts?caller_is_syncadapter=true' --where \"account_type='$ACCT' AND account_name='$ACCT'\"" > /dev/null

echo "== generating on-device seed script for $COUNT contacts"
NAMES_SHORT=("ماما" "بابا" "جدتي" "جدي" "خالتي" "عمتي" "خالي" "عمي" "أخويا" "أختي")
NAMES_LONG=("عبد الرحمن محمد عبد الله السيد علي" "فاطمة الزهراء محمد أحمد حسن إبراهيم" "مصطفى كامل عبد العزيز محمود الشاذلي" "أم كلثوم إبراهيم السيد البلتاجي أحمد")
PREFIXES=(010 011 012 015)

CHUNK=0; F="$SCRATCH/part0.sh"; printf '#!/system/bin/sh\n' > "$F"
i=0
while [ "$i" -lt "$COUNT" ]; do
  if [ $(( i % 400 )) -eq 0 ] && [ "$i" -gt 0 ]; then
    CHUNK=$((CHUNK+1)); F="$SCRATCH/part$CHUNK.sh"; printf '#!/system/bin/sh\n' > "$F"
  fi
  # Every name unique (index suffix) so the provider never auto-aggregates
  # synthetic raw contacts into one aggregate contact.
  if [ $(( i % 11 )) -eq 0 ]; then
    NAME="${NAMES_LONG[$(( i % 4 ))]} $i"
  else
    NAME="${NAMES_SHORT[$(( i % 10 ))]} $i"
  fi
  NUM1="${PREFIXES[$(( i % 4 ))]}$(printf '%08d' $(( 10000000 + i )) )"
  # Insert the raw contact with a unique sync1 marker, then read its id back
  # with a query - `content insert` output is not reliable across builds.
  # NOTE: the on-device `content` CLI catches provider exceptions and exits 0,
  # so `set -e` here cannot catch failed inserts; the host-side verification
  # at the end (exact account-scoped row counts) is the real guard.
  cat >> "$F" <<EOS
content insert --uri content://com.android.contacts/raw_contacts --bind account_type:s:$ACCT --bind account_name:s:$ACCT --bind sync1:s:seed_$i > /dev/null
ID=\$(content query --uri content://com.android.contacts/raw_contacts --projection _id --where "sync1='seed_$i'" | sed -n 's/.*_id=\([0-9]*\).*/\1/p')
[ -n "\$ID" ]
content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:\$ID --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:"$NAME" > /dev/null
content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:\$ID --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:$NUM1 --bind data2:i:2 --bind data3:s:"موبايل" > /dev/null
EOS
  # Every 5th contact gets a GENUINELY DIFFERENT second number: this
  # guarantees real multi-number contacts for the confirmation workload on
  # any device region (T-17 dedup merges only equivalent forms).
  if [ $(( i % 5 )) -eq 0 ]; then
    NUM2="${PREFIXES[$(( (i + 1) % 4 ))]}$(printf '%08d' $(( 20000000 + i )) )"
    echo "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:\$ID --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:$NUM2 --bind data2:i:2 --bind data3:s:\"عمل\" > /dev/null" >> "$F"
  fi
  # Every 7th gets an intl (+20) form of the FIRST number, every 13th a 0020
  # form: equivalent-forms load for the T-17 dedup path (region-dependent).
  if [ $(( i % 7 )) -eq 0 ]; then
    echo "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:\$ID --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:+20${NUM1#0} --bind data2:i:2 --bind data3:s:\"دولي\" > /dev/null" >> "$F"
  fi
  if [ $(( i % 13 )) -eq 0 ]; then
    echo "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:\$ID --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:0020${NUM1#0} --bind data2:i:2 --bind data3:s:\"دولي 00\" > /dev/null" >> "$F"
  fi
  i=$((i+1))
done

echo "== pushing $((CHUNK+1)) chunk(s) and running (slow for large counts)"
adbq shell "mkdir -p /data/local/tmp/mama-seed"
for P in "$SCRATCH"/part*.sh; do
  adbq push "$P" /data/local/tmp/mama-seed/ > /dev/null
  adbq shell "sh /data/local/tmp/mama-seed/$(basename "$P")"
done
adbq shell "rm -rf /data/local/tmp/mama-seed"

# Verification: exact account-scoped counts on the Data view (names AND
# phones), not just raw-contact count - an insert that the provider silently
# rejected must fail the run, not pass with missing rows.
EXP_PHONES=$(( COUNT + (COUNT-1)/5 + 1 + (COUNT-1)/7 + 1 + (COUNT-1)/13 + 1 ))
GOT=$(adbq shell "content query --uri content://com.android.contacts/raw_contacts --projection _id --where \"account_type='$ACCT' AND account_name='$ACCT' AND deleted=0\"" | grep -c 'Row:' || true)
NAMES=$(adbq shell "content query --uri content://com.android.contacts/data --projection _id --where \"account_type='$ACCT' AND mimetype='vnd.android.cursor.item/name'\"" | grep -c 'Row:' || true)
PHONES=$(adbq shell "content query --uri content://com.android.contacts/data --projection _id --where \"account_type='$ACCT' AND mimetype='vnd.android.cursor.item/phone_v2'\"" | grep -c 'Row:' || true)
echo "== seeded: raw_contacts=$GOT/$COUNT names=$NAMES/$COUNT phone_rows=$PHONES/$EXP_PHONES"
if [ "$GOT" != "$COUNT" ] || [ "$NAMES" != "$COUNT" ] || [ "$PHONES" != "$EXP_PHONES" ]; then
  echo "SEED VERIFICATION FAILED - the synthetic set is incomplete; re-run to clean and reseed" >&2
  exit 1
fi
