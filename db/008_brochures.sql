-- =====================================================================
-- 008 - Catálogos armados desde el panel (para el PDF y el enlace público)
-- Ejecutar en Supabase > SQL Editor ANTES de abrir "Catálogos" en el panel.
-- Es repetible (no borra ni cambia datos existentes).
--
-- Un catálogo tiene un nombre, un texto de inicio y las prendas elegidas. Cada prenda guarda:
--   - qué foto es la principal y cuáles son de apoyo (máximo 2). Se guarda el id de la foto, no su
--     ruta: si la foto se reemplaza (por ejemplo, al quitarle una marca de agua), el catálogo la sigue.
--   - dos textos propios de ese catálogo: la frase destacada y la ficha (material, colores, tallas...).
-- El precio y la disponibilidad NO se guardan: se leen siempre del producto, para que no queden viejos.
-- =====================================================================

create table if not exists "brochures" (
    "brochureId" uuid primary key default gen_random_uuid(),
    "name"       text        not null,
    "introText"  text,                                  -- texto de inicio ("Calidad y estilo...")
    "showPrices" boolean     not null default true,
    "createdAt"  timestamptz not null default now(),
    "updatedAt"  timestamptz not null default now()
);

create table if not exists "brochureItems" (
    "brochureItemId" uuid primary key default gen_random_uuid(),
    "brochureId"     uuid not null references "brochures" ("brochureId") on delete cascade,
    "productId"      uuid not null references "products" ("producttId") on delete cascade,
    "position"       int4 not null default 0,           -- orden dentro del catálogo
    "mainImageId"    uuid references "productImages" ("productImagesId") on delete set null,
    "extraImage1Id"  uuid references "productImages" ("productImagesId") on delete set null,
    "extraImage2Id"  uuid references "productImages" ("productImagesId") on delete set null,
    "highlightText"  text,                              -- frase destacada
    "detailsText"    text,                              -- ficha: material, colores, tallas...
    unique ("brochureId", "productId")
);

create index if not exists brochureitems_order_idx on "brochureItems" ("brochureId", "position");

-- Datos internos: sin acceso desde la API pública de Supabase (clave anon).
-- La aplicación se conecta directo a la base y no se ve afectada.
alter table "brochures"     enable row level security;
alter table "brochureItems" enable row level security;
revoke all on table "brochures"     from anon, authenticated;
revoke all on table "brochureItems" from anon, authenticated;
