#!/usr/bin/env python3
"""Lê o JSON do osv-scanner, imprime as vulnerabilidades encontradas e emite anotações do GitHub Actions.
Sai com código 1 se houver qualquer vulnerabilidade conhecida em dependência."""
import json
import sys

dados = json.load(open(sys.argv[1], encoding="utf-8"))
achados = []
for resultado in dados.get("results", []):
    for pacote in resultado.get("packages", []):
        info = pacote["package"]
        for v in pacote.get("vulnerabilities", []):
            achados.append((info["name"], info["version"], v["id"], (v.get("summary") or "")[:120]))
total_pacotes = sum(len(r.get("packages", [])) for r in dados.get("results", []))
print(f"Pacotes com vulnerabilidades conhecidas: {total_pacotes}; avisos: {len(achados)}")
for nome, versao, id_, resumo in sorted(set(achados)):
    print(f"::error title=Dependência vulnerável::{nome} {versao}: {id_} {resumo}")
sys.exit(1 if achados else 0)
