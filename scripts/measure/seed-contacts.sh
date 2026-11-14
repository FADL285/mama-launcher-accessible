#!/usr/bin/env bash
# T-04a: seed SYNTHETIC contacts into an emulator/test device contacts provider.
# Every row is marked with account_type=measure.local so cleanup never touches
# real data. Idempotent: re-running first deletes its own synthetic set.
#
# Usage: seed-contacts.sh [-s SERIAL] COUNT   (COUNT e.g. 50, 500, 2000)
#
# Photos, a slow/failing provider and corrupt/rotated streams are NOT seeded
# here: those need the separately-selected test-only fixture harness
# (docs/performance-baseline.md, "Pending selections").
set -euo pipefail

ADB=(adb)
while [ $# -gt 0 ]; do
  case "$1" in
    -s) ADB+=( -s "$2" ); shift 2 ;;
    *) COUNT="$1"; shift ;;
  esac
done
COUNT="${COUNT:?usage: seed-contacts.sh [-s SERIAL] COUNT}"

ACCT="measure.local"
TMP="/data/local/tmp/mama-seed"
NAMES_SHORT=("ماما" "بابا" "جدتي" "جدي" "خالتي" "عمتي" "خالي" "عمي" "أخويا" "أختي")
NAMES_LONG=("عبد الرحمن محمد عبد الله السيد علي" "فاطمة الزهراء محمد أحمد حسن إبراهيم" "مصطفى كامل عبد العزيز محمود الشاذلي" "أم كلثوم إبراهيم السيد البلتاجي أحمد")
PREFIXES=(010 011 012 015)

echo "== cleaning previous synthetic set ($ACCT)"
"${ADB[@]}" shell "content delete --uri content://com.android.contacts/raw_contacts --where \"account_type='$ACCT'\"" > /dev/null

echo "== generating on-device seed script for $COUNT contacts"
rm -rf /tmp/mama-seed-gen && mkdir -p /tmp/mama-seed-gen
CHUNK=0; N=0; F="/tmp/mama-seed-gen/part0.sh"; echo '#!/system/bin/sh' > "$F"
i=0
while [ "$i" -lt "$COUNT" ]; do
  if [ $(( i % 400 )) -eq 0 ] && [ "$i" -gt 0 ]; then
    CHUNK=$((CHUNK+1)); F="/tmp/mama-seed-gen/part$CHUNK.sh"; echo '#!/system/bin/sh' > "$F"
  fi
  if [ $(( i % 11 )) -eq 0 ]; then
    NAME="${NAMES_LONG[$(( i % 4 ))]}"
  else
    NAME="${NAMES_SHORT[$(( i % 10 ))]} $i"
  fi
  P1="${PREFIXES[$(( i % 4 ))]}"
  NUM1="${P1}$(printf '%08d' $(( 10000000 + i )) )"
  # raw contact row, then name row, then 1-3 phone rows (mixed national/intl forms)
  cat >> "$F" <<EOS
ID=\$(content insert --uri content://com.android.contacts/raw_contacts --bind account_type:s:$ACCT --bind account_name:s:$ACCT | sed 's/.*raw_contacts\///')
content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:\$ID --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:"$NAME" > /dev/null
content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:\$ID --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:$NUM1 --bind data2:i:2 --bind data3:s:"موبايل" > /dev/null
EOS
  # every 7th contact gets a second form of the same number (intl), every 13th a third
  if [ $(( i % 7 )) -eq 0 ]; then
    echo "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:\$ID --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:+20${NUM1#0} --bind data2:i:2 --bind data3:s:\"دولي\" > /dev/null" >> "$F"
  fi
  if [ $(( i % 13 )) -eq 0 ]; then
    echo "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:\$ID --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:0020${NUM1#0} --bind data2:i:2 --bind data3:s:\"دولي 00\" > /dev/null" >> "$F"
  fi
  i=$((i+1)); N=$((N+1))
done

echo "== pushing $((CHUNK+1)) chunk(s) and running (this is slow for large counts)"
"${ADB[@]}" shell "mkdir -p $TMP"
for P in /tmp/mama-seed-gen/part*.sh; do
  "${ADB[@]}" push "$P" "$TMP/" > /dev/null
  "${ADB[@]}" shell "sh $TMP/$(basename "$P")"
done
"${ADB[@]}" shell "rm -rf $TMP"; rm -rf /tmp/mama-seed-gen

GOT=$("${ADB[@]}" shell "content query --uri content://com.android.contacts/raw_contacts --projection _id --where \"account_type='$ACCT'\"" | grep -c 'Row:')
echo "== seeded $GOT synthetic contacts (expected $COUNT)"
[ "$GOT" = "$COUNT" ]
