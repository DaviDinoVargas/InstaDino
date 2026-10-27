-- Amplia os tipos aceitos em relacionamento. O SQLite não altera restrições CHECK; recria a tabela (nenhuma outra
-- tabela referencia relacionamento, então não há chaves estrangeiras de entrada a preservar).

CREATE TABLE relacionamento_novo (
    importacao_id TEXT NOT NULL REFERENCES importacao (id) ON DELETE CASCADE,
    tipo TEXT NOT NULL CHECK (tipo IN ('SEGUIDOR', 'SEGUINDO', 'SOLICITACAO_PENDENTE', 'BLOQUEADO', 'RESTRITO', 'FAVORITO',
                                       'DEIXEI_DE_SEGUIR_RECENTE', 'SOLICITACAO_RECENTE', 'SOLICITACAO_RECEBIDA')),
    usuario TEXT NOT NULL,
    url_perfil TEXT,
    data_registro INTEGER,
    PRIMARY KEY (importacao_id, tipo, usuario)
) WITHOUT ROWID;

INSERT INTO relacionamento_novo (importacao_id, tipo, usuario, url_perfil, data_registro)
    SELECT importacao_id, tipo, usuario, url_perfil, data_registro FROM relacionamento;

DROP TABLE relacionamento;

ALTER TABLE relacionamento_novo RENAME TO relacionamento;
