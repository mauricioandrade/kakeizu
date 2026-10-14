#!/bin/sh
# Daily backup: a pg_dump of the database plus a copy of the receipt photos, keeping the last 14 days.
# Run it from the project directory, e.g. from cron:  0 23 * * * cd /path/to/kakeizu && scripts/backup.sh
set -eu

BACKUP_DIR="${KAKEIZU_BACKUP_DIR:-$HOME/kakeizu-backups}"
STORAGE_DIR="${KAKEIZU_STORAGE_DIR:-data/images}"
KEEP_DAYS="${KAKEIZU_BACKUP_KEEP_DAYS:-14}"
stamp=$(date +%Y-%m-%d_%H%M)

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"   # the dump holds financial data

docker compose exec -T postgres pg_dump -U "${DB_USER:-kakeizu}" --format=custom kakeizu \
    > "$BACKUP_DIR/kakeizu-$stamp.dump"
tar -czf "$BACKUP_DIR/images-$stamp.tar.gz" -C "$(dirname "$STORAGE_DIR")" "$(basename "$STORAGE_DIR")"

find "$BACKUP_DIR" -name 'kakeizu-*.dump' -mtime +"$KEEP_DAYS" -delete
find "$BACKUP_DIR" -name 'images-*.tar.gz' -mtime +"$KEEP_DAYS" -delete
echo "Backup written to $BACKUP_DIR ($stamp)"
