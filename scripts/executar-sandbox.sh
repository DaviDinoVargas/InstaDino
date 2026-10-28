#!/usr/bin/env bash
# Sobe o backend de SANDBOX local (contas de demonstração, pagamentos de teste) e o aplicativo desktop.
# Uso: scripts/executar-sandbox.sh [--somente-backend] [--redefinir-senhas]
# Requisitos: JDK 21, Maven 3.9+. Em Linux o desktop precisa de um display (use xvfb-run para testes sem tela).
#
# Segurança: o backend escuta só em 127.0.0.1; nenhuma senha é escrita no log. As credenciais das contas de
# demonstração ficam em dados-backend/contas-semeadas.txt (somente o seu usuário lê; ignorado pelo Git).
# Para usar senhas suas, exporte antes INSTADINO_SENHA_MASTER, INSTADINO_SENHA_PREMIUM e INSTADINO_SENHA_FREE.
# --redefinir-senhas troca as senhas das três contas por senhas NOVAS e aleatórias (use se alguma senha foi divulgada;
# desfaça qualquer INSTADINO_SENHA_* antes) e as grava só em dados-backend/contas-semeadas.txt. Nunca cole esse arquivo em chat, issue ou commit.
set -euo pipefail
raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz"

export INSTADINO_DADOS_BACKEND="${INSTADINO_DADOS_BACKEND:-$raiz/dados-backend}"
porta="${INSTADINO_PORTA:-8443}"

somente_backend=0
for arg in "$@"; do
  case "$arg" in
    --somente-backend) somente_backend=1 ;;
    --redefinir-senhas) export INSTADINO_SEMEADURA_REDEFINIR=true ;;
    *) echo "Argumento desconhecido: $arg" >&2; exit 2 ;;
  esac
done

mkdir -p "$INSTADINO_DADOS_BACKEND"
echo "==> Compilando (sem testes)..."
mvn -B -q -DskipTests install -pl backend-api,desktop-app -am

echo "==> Iniciando o backend de sandbox em http://127.0.0.1:${porta} ..."
SPRING_PROFILES_ACTIVE=desenvolvimento INSTADINO_PORTA="$porta" PAYMENTS_ENABLED=true PAYMENTS_MODE=sandbox \
  java -jar "$raiz/backend-api/target/backend-api-0.1.0-SNAPSHOT.jar" >"$INSTADINO_DADOS_BACKEND/backend.log" 2>&1 &
pid_backend=$!
trap 'kill "$pid_backend" 2>/dev/null || true' EXIT

for _ in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:${porta}/v1/status" >/dev/null 2>&1; then pronto=1; break; fi
  sleep 1
done
if [ "${pronto:-0}" != "1" ]; then echo "O backend não iniciou. Veja $INSTADINO_DADOS_BACKEND/backend.log" >&2; exit 1; fi
echo "==> Backend pronto. Contas de demonstração: $INSTADINO_DADOS_BACKEND/contas-semeadas.txt"

if [ "$somente_backend" = "1" ]; then
  echo "Pressione Ctrl+C para encerrar o backend."; wait "$pid_backend"; exit 0
fi

echo "==> Iniciando o desktop..."
export INSTADINO_API="http://127.0.0.1:${porta}"
export INSTADINO_CHAVES_CONFIAVEIS="$INSTADINO_DADOS_BACKEND/chaves-dev"
JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dinstadino.modoInseguroDesenvolvimento=true" mvn -B -q -pl desktop-app javafx:run
