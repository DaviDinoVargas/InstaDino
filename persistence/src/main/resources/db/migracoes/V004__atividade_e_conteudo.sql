-- Atividade (curtidas, comentários, salvos, pesquisas, visualizações, interações com stories) e catálogo de conteúdo
-- (publicações, reels, stories e metadados das mídias). As mídias em si NÃO são guardadas: ficam no ZIP original.

CREATE TABLE atividade (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    importacao_id TEXT NOT NULL REFERENCES importacao (id) ON DELETE CASCADE,
    categoria TEXT NOT NULL,
    subtipo TEXT,
    conta TEXT,
    texto TEXT CHECK (texto IS NULL OR length(texto) <= 1000),
    referencia TEXT CHECK (referencia IS NULL OR length(referencia) <= 500),
    instante INTEGER
);

CREATE INDEX idx_atividade_busca ON atividade (importacao_id, categoria, instante);
CREATE INDEX idx_atividade_conta ON atividade (importacao_id, conta);

CREATE TABLE publicacao (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    importacao_id TEXT NOT NULL REFERENCES importacao (id) ON DELETE CASCADE,
    categoria TEXT NOT NULL CHECK (categoria IN ('PUBLICACOES', 'STORIES', 'REELS')),
    legenda TEXT CHECK (legenda IS NULL OR length(legenda) <= 2200),
    instante INTEGER
);

CREATE INDEX idx_publicacao_busca ON publicacao (importacao_id, categoria, instante);

CREATE TABLE midia (
    publicacao_id INTEGER NOT NULL REFERENCES publicacao (id) ON DELETE CASCADE,
    ordem INTEGER NOT NULL,
    caminho TEXT NOT NULL,
    tipo TEXT NOT NULL CHECK (tipo IN ('FOTO', 'VIDEO', 'OUTRO')),
    tamanho INTEGER,
    PRIMARY KEY (publicacao_id, ordem)
) WITHOUT ROWID;
