#!/usr/bin/env python3
"""Lê o JSON do osv-scanner, agrupa as vulnerabilidades por dependência e emite uma anotação do GitHub Actions por
dependência (o GitHub limita a 10 anotações por passo). Sai com código 1 se houver qualquer vulnerabilidade conhecida."""
import json
import sys
from collections import defaultdict

dados = json.load(open(sys.argv[1], encoding="utf-8"))
por_pacote = defaultdict(lambda: {"ids": set(), "corrigido": set()})
for resultado in dados.get("results", []):
    for pacote in resultado.get("packages", []):
        info = pacote["package"]
        chave = (info["name"], info["version"])
        for v in pacote.get("vulnerabilities", []):
            por_pacote[chave]["ids"].add(v["id"])
            for afetado in v.get("affected", []):
                if afetado.get("package", {}).get("name") != info["name"]:
                    continue
                for faixa in afetado.get("ranges", []):
                    for evento in faixa.get("events", []):
                        if "fixed" in evento:
                            por_pacote[chave]["corrigido"].add(evento["fixed"])
print(f"Dependências com vulnerabilidades conhecidas: {len(por_pacote)}")
for (nome, versao), d in sorted(por_pacote.items()):
    corrigido = ", ".join(sorted(d["corrigido"])) or "sem versão corrigida informada"
    ids = ", ".join(sorted(d["ids"]))
    print(f"::error title=Dependência vulnerável: {nome} {versao}::{len(d['ids'])} avisos ({ids}). Versões que corrigem algum deles: {corrigido}")
sys.exit(1 if por_pacote else 0)
