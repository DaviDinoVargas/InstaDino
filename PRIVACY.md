# Privacidade

Este documento descreve o comportamento **da versão atual**. Será revisado a cada mudança.

## Dados da exportação do Instagram

- São lidos e analisados **apenas no computador do usuário**.
- São gravados em um banco SQLite cifrado na pasta de dados do aplicativo (`%LOCALAPPDATA%\InstaDino` no Windows). A chave de cifragem é aleatória, fica fora do banco e é protegida pela DPAPI do Windows.
- **Nada é enviado à internet.** Nesta versão o aplicativo não possui nenhuma funcionalidade de rede.
- O programa não usa o login do Instagram, não acessa serviços privados da plataforma e não faz scraping.
- Categorias sensíveis (perfil, segurança, mensagens) são apenas catalogadas, sem leitura de conteúdo; quando passarem a ser processadas, exigirão consentimento explícito.

## Retenção e exclusão

- Os dados permanecem até o usuário excluí-los. Em Segurança e privacidade é possível excluir importações individuais ou **todos os dados locais** (banco e chave).
- O plano Free mantém somente a importação mais recente.
- Exclusão por sobrescrita é feita em melhor esforço; em SSDs a eliminação física não pode ser garantida (a destruição da chave inutiliza cópias do banco).

## Telemetria

Não há telemetria nem coleta de métricas de uso.

## Serviços comerciais (futuro)

Quando existirem contas, licenças e pagamentos, o servidor receberá somente o mínimo necessário (e-mail, estado da licença, identificadores de pagamento). Dados cartão/CVV nunca passam pelo InstaDino (checkout hospedado do provedor). Listas, mensagens, mídias e arquivos ZIP **nunca** serão enviados. Este documento será atualizado antes disso, em conformidade com a LGPD.
