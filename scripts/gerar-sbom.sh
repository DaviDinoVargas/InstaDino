#!/usr/bin/env bash
# Gera o SBOM (CycloneDX 1.6, JSON e XML) com todas as dependências do projeto, em target/bom.json e target/bom.xml.
# Uso: scripts/gerar-sbom.sh
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -B -ntp -q -DskipTests org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom
echo "SBOM gerado:"
ls -l target/bom.json target/bom.xml
python3 - <<'PY'
import json
b = json.load(open("target/bom.json", encoding="utf-8"))
sem = [c["name"] for c in b["components"] if not c.get("licenses")]
print(f"{b['bomFormat']} {b['specVersion']}: {len(b['components'])} componentes; {len(sem)} sem licença declarada no POM: {', '.join(sorted(sem)) or '-'}")
PY
