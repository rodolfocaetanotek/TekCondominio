#!/usr/bin/env bash
# Restauração do Tek Condomínio a partir de um backup gerado por backup.sh.
# Uso: ./scripts/restore.sh <pasta_backups> <carimbo>   ex.: ./scripts/restore.sh ./backups 20261027-020000
# ATENÇÃO: substitui o banco e os arquivos atuais. Teste periodicamente em um ambiente separado.
set -euo pipefail

DIR="${1:?informe a pasta dos backups}"
STAMP="${2:?informe o carimbo do backup}"
DB="$DIR/tekcondo-db-$STAMP.dump"
FILES="$DIR/tekcondo-storage-$STAMP.tar.gz"

( cd "$DIR" && sha256sum -c "tekcondo-$STAMP.sha256" )

read -r -p "Isto substituirá os dados atuais. Digite RESTAURAR para continuar: " answer
[ "$answer" = "RESTAURAR" ] || { echo "Cancelado."; exit 1; }

echo "[1/4] Parando a API..."
docker compose stop backend

echo "[2/4] Restaurando banco..."
docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" --if-exists tekcondo && createdb -U "$POSTGRES_USER" tekcondo'
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d tekcondo --no-owner' < "$DB"

echo "[3/4] Restaurando arquivos..."
docker compose run --rm -T --entrypoint sh backend -c 'rm -rf /data/storage/* && tar -xzf - -C /data' < "$FILES"

echo "[4/4] Subindo a API..."
docker compose start backend
echo "Restauração concluída."
