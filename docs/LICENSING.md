# Licenciamento

O Premium é concedido por um **documento de licença assinado pelo servidor**. Nenhuma preferência, arquivo ou flag local concede Premium (LIC-03).

## Formato

JSON canônico + assinatura **Ed25519** calculada sobre os bytes exatos do payload (ambos em base64url), com `kid` identificando a chave. O payload contém produto, plano, titular, dispositivo, emissão e validade.

## O que o cliente verifica (`VerificadorDeLicenca`)

1. algoritmo esperado e `kid` conhecido (chaves públicas embutidas);
2. assinatura sobre os bytes recebidos;
3. produto, plano e titular (conta autenticada);
4. vínculo com **este** dispositivo;
5. datas de emissão e validade, com detecção de regressão de relógio (tolerância de 5 min);
6. janela offline: validade de 7 dias + tolerância offline de 7 dias.

Falhou qualquer etapa → o app volta para Free. **Dados locais nunca ficam bloqueados** por licença expirada ou servidor indisponível (LIC-05).

## Servidor

- Emissão em `POST /v1/licencas`; só quando há direito ativo (assinatura paga ou concessão administrativa).
- Máximo de **3 dispositivos** por conta (`GET/DELETE /v1/dispositivos`).
- Revogação: reembolso, disputa e ação administrativa revogam as licenças emitidas.
- A chave privada existe só no ambiente do backend. Em produção é obrigatória e o servidor se recusa a iniciar sem ela. Em desenvolvimento é gerada em `dados-backend/chaves-dev/<kid>.key` (ignorado pelo Git).
- Rotação: nova chave recebe novo `kid`; o cliente aceita o conjunto de chaves públicas embutido.

## Cache no cliente

A última licença válida é guardada protegida por DPAPI para uso offline dentro da janela. Quem controla a conta do Windows consegue editar esse arquivo — o limite é declarado em R21/R24.

## Pendência

`licensing-client/src/main/resources/chaves-publicas/indice.txt` está **vazio**: ainda não existe chave de produção. Antes do primeiro release é preciso gerar o par em ambiente controlado, embutir a pública e publicar o `kid`. Sem isso, builds de produção nunca ativam Premium (falha fechada). Em desenvolvimento use `INSTADINO_CHAVES_CONFIAVEIS` (só aceito com o modo de desenvolvimento).
