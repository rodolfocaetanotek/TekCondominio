#!/usr/bin/env bash
# Backup do Tek Condomínio (Docker Compose): banco PostgreSQL + arquivos (documentos/anexos).
# Uso: ./scripts/backup.sh [pasta_destino]   (padrão: ./backups)
# Agende diariamente (cron) e copie a pasta para um local externo (outro servidor/nuvem) com retenção.
set -euo pipefail

DEST="${1:-./backups}"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$DEST"

echo "[1/3] Exportando banco de dados..."
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d tekcondo --format=custom --no-owner' > "$DEST/tekcondo-db-$STAMP.dump"

echo "[2/3] Copiando arquivos (documentos e anexos)..."
docker compose exec -T backend sh -c 'tar -czf - -C /data storage' > "$DEST/tekcondo-storage-$STAMP.tar.gz"

echo "[3/3] Gerando somas de verificação..."
( cd "$DEST" && sha256sum "tekcondo-db-$STAMP.dump" "tekcondo-storage-$STAMP.tar.gz" > "tekcondo-$STAMP.sha256" )

# Retenção local: mantém os últimos 14 backups.
ls -1t "$DEST"/tekcondo-db-*.dump 2>/dev/null | tail -n +15 | while read -r old; do
  stamp="${old##*tekcondo-db-}"; stamp="${stamp%.dump}"
  rm -f "$DEST/tekcondo-db-$stamp.dump" "$DEST/tekcondo-storage-$stamp.tar.gz" "$DEST/tekcondo-$stamp.sha256"
done

echo "Backup concluído: $DEST (carimbo $STAMP)"
