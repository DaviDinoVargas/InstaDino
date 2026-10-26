#!/usr/bin/env bash
# Instala o hook de pré-commit que executa a verificação de segredos.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
cat > .git/hooks/pre-commit <<'HOOK'
#!/usr/bin/env bash
exec "$(git rev-parse --show-toplevel)/scripts/verificar-segredos.sh"
HOOK
chmod +x .git/hooks/pre-commit
echo "Hook de pré-commit instalado."
