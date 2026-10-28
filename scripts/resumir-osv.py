#!/usr/bin/env python3
"""Lê o JSON do osv-scanner, agrupa as vulnerabilidades por dependência e emite uma anotação do GitHub Actions por
dependência (o GitHub limita a 10 anotações por passo). Sai com código 1 se houver qualquer vulnerabilidade conhecida."""
import datetime
import json
import sys
from collections import defaultdict

dados = json.load(open(sys.argv[1], encoding="utf-8"))
excecoes = {}
vencidas = []
if len(sys.argv) > 2:
    hoje = datetime.date.today()
    for e in json.load(open(sys.argv[2], encoding="utf-8"))["excecoes"]:
        if not e.get("motivo") or not e.get("revisar_ate"):
            sys.exit(f"Exceção {e.get('id')} sem motivo ou data de revisão.")
        if datetime.date.fromisoformat(e["revisar_ate"]) < hoje:
            vencidas.append(e["id"])
        else:
            excecoes[e["id"]] = e
por_pacote = defaultdict(lambda: {"ids": set(), "corrigido": set()})
for resultado in dados.get("results", []):
    for pacote in resultado.get("packages", []):
        info = pacote["package"]
        chave = (info["name"], info["version"])
        for v in pacote.get("vulnerabilities", []):
            if v["id"] in excecoes:
                print(f"Exceção aceita para {v['id']} em {info['name']}: {excecoes[v['id']]['motivo'][:100]}… (revisar até {excecoes[v['id']]['revisar_ate']})")
                continue
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
for id_ in vencidas:
    print(f"::error title=Exceção de auditoria vencida::{id_} passou da data de revisão em config/osv-excecoes.json")
sys.exit(1 if por_pacote or vencidas else 0)
