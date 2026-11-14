#!/usr/bin/env bash
# T-04a: seed SYNTHETIC contacts into an emulator/test device contacts provider.
# Every row uses account_type/account_name=measure.local so cleanup never
# touches real data. Idempotent: re-running first deletes its own set.
#
# Usage: seed-contacts.sh [-s SERIAL] COUNT   (COUNT e.g. 50, 500, 2000)
#
# Photos, a slow/failing provider and corrupt/rotated streams are NOT seeded
# here: those need the separately-selected test-only fixture harness
# (docs/performance-baseline.md, "Pending selections").
set -euo pipefail
exec 9> /tmp/mama-seed.lock && flock 9   # one seeding run per host

ADB=(adb); COUNT=""
while [ $# -gt 0 ]; do
  case "$1" in
    -s) ADB+=( -s "$2" ); shift 2 ;;
    *) COUNT="$1"; shift ;;
  esac
done
case "${COUNT:-}" in ''|*[!0-9]*) echo "usage: seed-contacts.sh [-s SERIAL] COUNT (positive integer)" >&2; exit 1 ;; esac
[ "$COUNT" -gt 0 ] || { echo "COUNT must be positive" >&2; exit 1; }

ACCT="measure.local"
SCRATCH="$(mktemp -d)"; trap 'rm -rf "$SCRATCH"' EXIT

echo "== cleaning previous synthetic set ($ACCT)"
# caller_is_syncadapter=true makes the delete REAL, not a deleted=1 tombstone.
"${ADB[@]}" shell "content delete --uri 'content://com.android.contacts/raw_contacts?caller_is_syncadapter=true' --where \"account_type='$ACCT' AND account_name='$ACCT'\"" > /dev/null

echo "== generating on-device seed script for $COUNT contacts"
NAMES_SHORT=("ماما" "بابا" "جدتي" "جدي" "خالتي" "عمتي" "خالي" "عمي" "أخويا" "أختي")
NAMES_LONG=("عبد الرحمن محمد عبد الله السيد علي" "فاطمة الزهراء محمد أحمد حسن إبراهيم" "مصطفى كامل عبد العزيز محمود الشاذلي" "أم كلثوم إبراهيم السيد البلتاجي أحمد")
PREFIXES=(010 011 012 015)

CHUNK=0; F="$SCRATCH/part0.sh"; printf '#!/system/bin/sh\nset -e\n' > "$F"
i=0
while [ "$i" -lt "$COUNT" ]; do
  if [ $(( i % 400 )) -eq 0 ] && [ "$i" -gt 0 ]; then
    CHUNK=$((CHUNK+1)); F="$SCRATCH/part$CHUNK.sh"; printf '#!/system/bin/sh\nset -e\n' > "$F"
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
  cat >> "$F" <<EOS
content insert --uri content://com.android.contacts/raw_contacts --bind account_type:s:$ACCT --bind account_name:s:$ACCT --bind sync1:s:seed_$i > /dev/null
ID=\$(content query --uri content://com.android.contacts/raw_contacts --projection _id --where "sync1='seed_$i'" | sed -n 's/.*_id=\([0-9]*\).*/\1/p')
[ -n "\$ID" ]
content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:\$ID --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:"$NAME" > /dev/null
content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:\$ID --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:$NUM1 --bind data2:i:2 --bind data3:s:"موبايل" > /dev/null
EOS
  # every 7th contact gets a second (intl) form of the same number, every 13th a third
  if [ $(( i % 7 )) -eq 0 ]; then
    echo "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:\$ID --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:+20${NUM1#0} --bind data2:i:2 --bind data3:s:\"دولي\" > /dev/null" >> "$F"
  fi
  if [ $(( i % 13 )) -eq 0 ]; then
    echo "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:\$ID --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:0020${NUM1#0} --bind data2:i:2 --bind data3:s:\"دولي 00\" > /dev/null" >> "$F"
  fi
  i=$((i+1))
done

echo "== pushing $((CHUNK+1)) chunk(s) and running (slow for large counts)"
"${ADB[@]}" shell "mkdir -p /data/local/tmp/mama-seed"
for P in "$SCRATCH"/part*.sh; do
  "${ADB[@]}" push "$P" /data/local/tmp/mama-seed/ > /dev/null
  "${ADB[@]}" shell "sh /data/local/tmp/mama-seed/$(basename "$P")"
done
"${ADB[@]}" shell "rm -rf /data/local/tmp/mama-seed"

GOT=$("${ADB[@]}" shell "content query --uri content://com.android.contacts/raw_contacts --projection _id --where \"account_type='$ACCT' AND account_name='$ACCT' AND deleted=0\"" | grep -c 'Row:' || true)
PHONES=$("${ADB[@]}" shell "content query --uri content://com.android.contacts/data --projection _id --where \"mimetype='vnd.android.cursor.item/phone_v2'\"" | grep -c 'Row:' || true)
echo "== seeded $GOT synthetic contacts (expected $COUNT), phone rows on device: $PHONES"
[ "$GOT" = "$COUNT" ]
