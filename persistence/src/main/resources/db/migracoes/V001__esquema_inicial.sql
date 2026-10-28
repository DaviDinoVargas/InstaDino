-- Esquema inicial do banco local. Todos os dados da exportação permanecem neste arquivo cifrado.

CREATE TABLE perfil_local (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nome TEXT NOT NULL CHECK (length(nome) BETWEEN 1 AND 100),
    usuario_instagram TEXT,
    criado_em TEXT NOT NULL
);

CREATE TABLE importacao (
    id TEXT PRIMARY KEY,
    perfil_id INTEGER NOT NULL REFERENCES perfil_local (id) ON DELETE CASCADE,
    importada_em TEXT NOT NULL,
    versao_parser TEXT NOT NULL,
    origem TEXT NOT NULL,
    nome_arquivo TEXT,
    tamanho_bytes INTEGER,
    registros_validos INTEGER NOT NULL DEFAULT 0 CHECK (registros_validos >= 0),
    registros_invalidos INTEGER NOT NULL DEFAULT 0 CHECK (registros_invalidos >= 0),
    resumo_json TEXT
);

CREATE INDEX idx_importacao_perfil ON importacao (perfil_id, importada_em);

CREATE TABLE importacao_categoria (
    importacao_id TEXT NOT NULL REFERENCES importacao (id) ON DELETE CASCADE,
    categoria TEXT NOT NULL,
    arquivos INTEGER NOT NULL DEFAULT 0,
    registros INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (importacao_id, categoria)
);

CREATE TABLE relacionamento (
    importacao_id TEXT NOT NULL REFERENCES importacao (id) ON DELETE CASCADE,
    tipo TEXT NOT NULL CHECK (tipo IN ('SEGUIDOR', 'SEGUINDO', 'SOLICITACAO_PENDENTE', 'BLOQUEADO', 'RESTRITO', 'FAVORITO')),
    usuario TEXT NOT NULL,
    url_perfil TEXT,
    data_registro INTEGER,
    PRIMARY KEY (importacao_id, tipo, usuario)
) WITHOUT ROWID;

CREATE TABLE preferencia (
    chave TEXT PRIMARY KEY,
    valor TEXT NOT NULL
);

CREATE TABLE consentimento (
    funcionalidade TEXT PRIMARY KEY,
    concedido_em TEXT NOT NULL
);

CREATE TABLE evento_seguranca (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ocorrido_em TEXT NOT NULL,
    tipo TEXT NOT NULL,
    detalhe TEXT
);
