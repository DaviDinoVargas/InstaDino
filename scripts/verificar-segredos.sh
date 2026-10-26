#!/usr/bin/env bash
# Verifica o índice do Git em busca de segredos e arquivos proibidos.
# Uso: scripts/verificar-segredos.sh [--historico]
#   (sem argumentos)  analisa os arquivos rastreados no índice atual
#   --historico       analisa também as mensagens de commit (coautoria automática, etc.)
# Código de saída: 0 = limpo, 1 = problemas encontrados.
set -uo pipefail

cd "$(git rev-parse --show-toplevel)"
falhas=0

relatar() { echo "PROBLEMA: $1" >&2; falhas=$((falhas + 1)); }

# 1) Nomes de arquivo proibidos
while IFS= read -r arquivo; do
  case "$arquivo" in
    shared-test-fixtures/*) ;;  # fixtures sintéticas são permitidas
    *.zip|*.db|*.sqlite|*.sqlite3|*.db-wal|*.db-shm|*.pem|*.key|*.p12|*.pfx|*.jks|*.keystore|*.dump|*.bak|*.log)
      relatar "arquivo proibido versionado: $arquivo" ;;
    .env|.env.*)
      [ "$arquivo" = ".env.exemplo" ] || relatar "arquivo de ambiente versionado: $arquivo" ;;
    contas-semeadas*|segredos/*|credenciais/*)
      relatar "arquivo de credenciais versionado: $arquivo" ;;
  esac
done < <(git ls-files)

# 2) Padrões de segredos no conteúdo
#    Arquivos de documentação e este script são ignorados para evitar falsos positivos nos exemplos.
padroes=(
  '-----BEGIN (RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----'
  'AKIA[0-9A-Z]{16}'
  'sk_live_[0-9A-Za-z]{10,}'
  'rk_live_[0-9A-Za-z]{10,}'
  'whsec_[0-9A-Za-z]{16,}'
  'ghp_[0-9A-Za-z]{30,}'
  'github_pat_[0-9A-Za-z_]{40,}'
  'xox[baprs]-[0-9A-Za-z-]{10,}'
  'APP_USR-[0-9]{6,}-[0-9]{6}-[0-9a-f]{20,}'
  'AIza[0-9A-Za-z_-]{35}'
)
for padrao in "${padroes[@]}"; do
  achados=$(git grep -nIE -e "$padrao" -- . ':(exclude)scripts/verificar-segredos.sh' ':(exclude)scripts/testar-verificador.sh' ':(exclude)*/src/test/*' 2>/dev/null || true)
  if [ -n "$achados" ]; then
    relatar "possível segredo (padrão $padrao):"
    echo "$achados" | sed 's/^/    /' >&2
  fi
done

# 3) Atribuições de senha/segredo literais em arquivos de configuração e código de produção
achados=$(git grep -nIiE '(password|senha|secret|segredo|api[_-]?key|token)[A-Za-z_.-]*[[:space:]]*[=:][[:space:]]*["'"'"']?[A-Za-z0-9/+_.!@#$%^&*-]{12,}["'"'"']?' \
  -- '*.properties' '*.yml' '*.yaml' '*.xml' '*.json' '*.env' '*.sh' '*.ps1' '*.bat' \
  ':(exclude)scripts/verificar-segredos.sh' ':(exclude)scripts/testar-verificador.sh' ':(exclude)pom.xml' ':(exclude)*/pom.xml' 2>/dev/null \
  | grep -viE 'changeme|exemplo|example|placeholder|\$\{|<[a-z_]+>|SEU_|\.\.\.' || true)
if [ -n "$achados" ]; then
  relatar "possível credencial literal em configuração:"
  echo "$achados" | sed 's/^/    /' >&2
fi

# 4) Referências proibidas em arquivos versionados (requisito SUP-05)
achados=$(git grep -nIiE 'co-authored-by|generated (with|by) (claude|chatgpt|copilot)|claude code|anthropic' \
  -- . ':(exclude)scripts/verificar-segredos.sh' ':(exclude)scripts/testar-verificador.sh' ':(exclude).github/workflows/*' 2>/dev/null || true)
if [ -n "$achados" ]; then
  relatar "referência proibida a ferramentas/coautoria:"
  echo "$achados" | sed 's/^/    /' >&2
fi

# 5) Mensagens de commit (opcional)
if [ "${1:-}" = "--historico" ]; then
  if git log --format='%H %B' | grep -qiE 'co-authored-by|claude|anthropic'; then
    relatar "mensagens de commit contêm coautoria automática ou identificadores de agentes"
  fi
fi

if [ "$falhas" -gt 0 ]; then
  echo "Verificação de segredos: $falhas problema(s)." >&2
  exit 1
fi
echo "Verificação de segredos: nenhum problema encontrado."
