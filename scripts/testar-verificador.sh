#!/usr/bin/env bash
# Autoteste do verificador: cria um repositório temporário com violações e confirma que são detectadas.
set -uo pipefail
raiz="$(git rev-parse --show-toplevel)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cd "$tmp"
git init -q . && git config user.email t@t && git config user.name t && git config commit.gpgsign false
mkdir -p scripts && cp "$raiz/scripts/verificar-segredos.sh" scripts/

caso() { # nome, comando que cria o arquivo, esperado (0 limpo / 1 falha)
  local nome="$1" criar="$2" esperado="$3"
  git rm -q -r -f --cached . >/dev/null 2>&1 || true
  find . -mindepth 1 -maxdepth 1 ! -name .git ! -name scripts -exec rm -rf {} +
  eval "$criar"
  git add -A >/dev/null 2>&1
  scripts/verificar-segredos.sh >/dev/null 2>&1
  local codigo=$?
  if [ "$codigo" -eq "$esperado" ]; then echo "ok   - $nome"; else echo "FALHA - $nome (esperado $esperado, obtido $codigo)"; resultado=1; fi
}
resultado=0
caso "arquivo limpo"                 'echo "classe Limpa {}" > A.java' 0
caso "chave privada PEM"             'printf "%s\n" "-----BEGIN PRIVATE KEY-----" > k.txt' 1
caso "arquivo .env"                  'echo X=1 > .env' 1
caso ".env.exemplo permitido"        'echo X=1 > .env.exemplo' 0
caso "banco sqlite"                  'echo x > dados.db' 1
caso "zip fora das fixtures"         'echo x > export.zip' 1
caso "zip nas fixtures sintéticas"   'mkdir -p shared-test-fixtures && echo x > shared-test-fixtures/ok.zip' 0
caso "chave stripe live"             'echo "k=sk_live_abcdefghijklmnop" > c.txt' 1
caso "token github"                  'echo "t=ghp_abcdefghijklmnopqrstuvwxyzABCDEF0123" > c.txt' 1
caso "senha literal em properties"  'echo "db.password=Sup3rS3creta!2024" > app.properties' 1
caso "senha via variável permitida"  'echo "db.password=\${DB_SENHA}" > app.properties' 0
caso "menção proibida"               'echo "Co-authored-by: alguem" > N.md' 1
exit $resultado
