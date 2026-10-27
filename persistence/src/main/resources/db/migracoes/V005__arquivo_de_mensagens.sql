-- Módulo opcional de mensagens (consentimento explícito + Premium). Guarda somente AGREGADOS por conversa e por mês.
-- O texto das mensagens, os nomes de arquivos de mídia, as reações e os links nunca são gravados.

CREATE TABLE conversa (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    importacao_id TEXT NOT NULL REFERENCES importacao (id) ON DELETE CASCADE,
    titulo TEXT NOT NULL CHECK (length(titulo) <= 200),
    participantes INTEGER NOT NULL CHECK (participantes >= 1),
    total INTEGER NOT NULL CHECK (total >= 0),
    enviadas INTEGER NOT NULL CHECK (enviadas >= -1), -- -1: titular não identificado
    midias INTEGER NOT NULL CHECK (midias >= 0),
    primeiro INTEGER,
    ultimo INTEGER
);

CREATE INDEX idx_conversa_importacao ON conversa (importacao_id, total);

CREATE TABLE conversa_mes (
    conversa_id INTEGER NOT NULL REFERENCES conversa (id) ON DELETE CASCADE,
    mes TEXT NOT NULL CHECK (length(mes) = 7),
    total INTEGER NOT NULL CHECK (total >= 0),
    enviadas INTEGER NOT NULL CHECK (enviadas >= -1),
    PRIMARY KEY (conversa_id, mes)
) WITHOUT ROWID;
