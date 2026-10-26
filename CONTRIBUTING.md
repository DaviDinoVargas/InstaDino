# Contribuindo

## Branches (em português)

- `main`: somente versões integradas, testadas e revisadas.
- `desenvolvimento`: integração das funcionalidades concluídas.
- `funcionalidade/<assunto>`, `seguranca/<assunto>`, `correcao/<assunto>`, `documentacao/<assunto>`: uma unidade funcional por branch.

## Commits

Mensagens objetivas em português, no formato Conventional Commits (`feat(escopo): ...`, `fix`, `test`, `docs`, `build`, `chore`), descrevendo a mudança técnica. Não incluir trailers de coautoria automática nem referências a ferramentas de geração de código (o CI verifica).

## Antes de cada commit

1. Revisar o diff.
2. Rodar os testes do módulo alterado e, antes de integrar, a suíte completa (`xvfb-run -a mvn -B verify`).
3. Rodar `scripts/verificar-segredos.sh` (ou instalar o hook com `scripts/instalar-hooks.sh`).
4. Confirmar que nenhum segredo, ZIP real, banco local ou log foi adicionado. **O repositório é público.**
5. Conferir que só arquivos pertinentes ao objetivo da branch estão no commit.

## Regras de segurança

Ver `docs/security/SECURITY_REQUIREMENTS.md`. Alterações em importação, banco, autenticação, licenças ou pagamentos exigem testes dos requisitos correspondentes. Não enfraqueça um controle para acelerar a entrega.
