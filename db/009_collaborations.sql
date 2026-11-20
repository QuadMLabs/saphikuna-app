-- =====================================================================
-- 009 - Colaboraciones: prendas hechas por encargo para otras marcas (solo para mostrar)
-- Ejecutar en Supabase > SQL Editor ANTES de abrir "Colaboraciones" en el panel.
-- Es repetible (no borra ni cambia datos existentes).
--
-- No forman parte del catálogo: no tienen precio, tallas, categoría ni disponibilidad, y no
-- aparecen en los productos, los encargos ni los PDF.
--   - collaborations      : la marca (solo el nombre) y si se muestra o no en la tienda
--   - collabPieces        : cada prenda hecha para esa marca (nombre, descripción, fotos y colores)
--   - collabPieceColors   : colores de la prenda (se reutiliza la lista de "Colores")
--   - collabPieceImages   : fotos de la prenda (la principal queda en primaryImagePath)
-- =====================================================================

create table if not exists "collaborations" (
    "collaborationId" uuid primary key default gen_random_uuid(),
    "brandName"       text        not null,
    "visible"         boolean     not null default true,   -- se muestra en la tienda
    "createdAt"       timestamptz not null default now(),
    "updatedAt"       timestamptz not null default now()
);

create table if not exists "collabPieces" (
    "collabPieceId"    uuid primary key default gen_random_uuid(),
    "collaborationId"  uuid not null references "collaborations" ("collaborationId") on delete cascade,
    "name"             text not null,
    "description"      text,
    "primaryImagePath" text,
    "createdAt"        timestamptz not null default now(),
    "updatedAt"        timestamptz not null default now()
);

create index if not exists collabpieces_brand_idx on "collabPieces" ("collaborationId", "createdAt");

create table if not exists "collabPieceColors" (
    "collabPieceId" uuid not null references "collabPieces" ("collabPieceId") on delete cascade,
    "colorId"       uuid not null references "colors" ("colorId") on delete cascade,
    primary key ("collabPieceId", "colorId")
);

create table if not exists "collabPieceImages" (
    "collabPieceImageId" uuid primary key default gen_random_uuid(),
    "collabPieceId"      uuid not null references "collabPieces" ("collabPieceId") on delete cascade,
    "storagePath"        text not null,
    "sortOrder"          int2 not null default 0,
    "createdAt"          timestamptz not null default now()
);

create index if not exists collabpieceimages_piece_idx on "collabPieceImages" ("collabPieceId", "sortOrder");

-- Datos internos: sin acceso desde la API pública de Supabase (clave anon).
-- La aplicación se conecta directo a la base y no se ve afectada.
-- (La tienda los leerá después por una vista de solo lectura, como store_products.)
alter table "collaborations"    enable row level security;
alter table "collabPieces"      enable row level security;
alter table "collabPieceColors" enable row level security;
alter table "collabPieceImages" enable row level security;
revoke all on table "collaborations"    from anon, authenticated;
revoke all on table "collabPieces"      from anon, authenticated;
revoke all on table "collabPieceColors" from anon, authenticated;
revoke all on table "collabPieceImages" from anon, authenticated;
