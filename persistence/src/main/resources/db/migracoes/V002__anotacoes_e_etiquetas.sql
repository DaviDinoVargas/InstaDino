-- Favoritos, etiquetas e notas privadas sobre contas. Ligadas ao perfil local e ao nome de usuário normalizado
-- (não a uma importação), então sobrevivem a novas importações e à exclusão de importações.

CREATE TABLE etiqueta (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    perfil_id INTEGER NOT NULL REFERENCES perfil_local (id) ON DELETE CASCADE,
    nome TEXT NOT NULL CHECK (length(nome) BETWEEN 1 AND 40),
    UNIQUE (perfil_id, nome COLLATE NOCASE)
);

CREATE TABLE anotacao_conta (
    perfil_id INTEGER NOT NULL REFERENCES perfil_local (id) ON DELETE CASCADE,
    usuario TEXT NOT NULL CHECK (length(usuario) BETWEEN 1 AND 30),
    favorito INTEGER NOT NULL DEFAULT 0 CHECK (favorito IN (0, 1)),
    nota TEXT CHECK (nota IS NULL OR length(nota) <= 2000),
    atualizada_em TEXT NOT NULL,
    PRIMARY KEY (perfil_id, usuario)
) WITHOUT ROWID;

CREATE TABLE conta_etiqueta (
    perfil_id INTEGER NOT NULL,
    usuario TEXT NOT NULL,
    etiqueta_id INTEGER NOT NULL REFERENCES etiqueta (id) ON DELETE CASCADE,
    PRIMARY KEY (perfil_id, usuario, etiqueta_id),
    FOREIGN KEY (perfil_id) REFERENCES perfil_local (id) ON DELETE CASCADE
) WITHOUT ROWID;

CREATE INDEX idx_conta_etiqueta_etiqueta ON conta_etiqueta (etiqueta_id);
