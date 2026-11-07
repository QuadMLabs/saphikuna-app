-- =====================================================================
-- 005 - "Nuevo hasta" en productos y tabla de ajustes
-- Ejecutar en Supabase > SQL Editor ANTES de usar la versión nueva de la app.
-- Es repetible.
-- =====================================================================

-- 1) Fecha hasta la cual un producto se muestra como "nuevo" en la tienda.
--    NULL = no es nuevo. Los productos migrados quedan en NULL (no son nuevos).
alter table "products" add column if not exists "newUntil" date;

-- 2) La etiqueta NEW ya no se escribe a mano: "nuevo" se decide con la fecha.
--    Se limpia en los productos migrados que la traían del JSON de la tienda.
update "products" set "label" = null where upper(trim("label")) = 'NEW';

-- 3) Ajustes de la aplicación (clave/valor)
create table if not exists "settings" (
    "key"       text primary key,
    "value"     text        not null,
    "updatedAt" timestamptz not null default now()
);

-- Días que un producto nuevo se muestra como nuevo (se puede cambiar en Configuración)
insert into "settings" ("key", "value") values ('newProductDays', '15')
on conflict ("key") do nothing;

-- Datos internos: sin acceso desde la API pública de Supabase (clave anon).
-- La aplicación se conecta directo a la base y no se ve afectada.
alter table "settings" enable row level security;
