#!/usr/bin/env bash
# Gera uma imagem de aplicativo (app-image) em Linux. NÃO é o produto: o alvo é Windows (scripts/empacotar-windows.ps1).
# Serve para validar a montagem do pacote, o runtime mínimo (jdeps/jlink) e a inicialização do aplicativo empacotado.
set -euo pipefail
raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$raiz"

versao_pom="$(mvn -B -q -o help:evaluate -Dexpression=project.version -DforceStdout 2>/dev/null || true)"
versao="${INSTADINO_VERSAO:-${versao_pom%-SNAPSHOT}}"
versao="${versao:-0.1.0}"

mvn -B -ntp -DskipTests -Pempacotamento -pl desktop-app -am package
entrada="desktop-app/target/entrada-jpackage"
principal="$(ls desktop-app/target/desktop-app-*.jar | grep -v -- '-sources' | head -1)"
cp "$principal" "$entrada/"
jar_principal="$(basename "$principal")"

modulos="$(jdeps --ignore-missing-deps --multi-release 21 --print-module-deps --class-path "$entrada/*" "$principal")"
# jdk.unsupported: exigido pelo JavaFX; jdk.crypto.ec e java.net.http: TLS e cliente da API; jdk.localedata: formatos pt-BR.
modulos="$(printf "%s,jdk.unsupported,jdk.crypto.ec,java.net.http,jdk.localedata" "$modulos" | tr "," "\n" | sort -u | paste -sd, -)"
echo "Módulos do runtime: $modulos"

destino="dist"
rm -rf "$destino" && mkdir -p "$destino"
jpackage --type app-image --name InstaDino --app-version "$versao" --vendor "InstaDino" \
  --description "Análise local da exportação oficial do Instagram" \
  --input "$entrada" --main-jar "$jar_principal" --main-class br.com.instadino.desktop.Principal \
  --java-options "-Dfile.encoding=UTF-8" --add-modules "$modulos" --dest "$destino"

( cd "$destino" && find InstaDino -type f -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS.txt )
echo "Imagem gerada em $destino/InstaDino ($(du -sh "$destino/InstaDino" | cut -f1))"
