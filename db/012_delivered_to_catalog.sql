-- =====================================================================
-- 012 - Encargos personales entregados pasan solos al catálogo (ocultos hasta completarlos)
-- Ejecutar en Supabase > SQL Editor. Es repetible (no borra ni cambia datos existentes).
--
-- Cuando un encargo de un cliente personal queda "Entregado", la aplicación crea un producto
-- en el catálogo: sin fotos, en estado Inactivo (la tienda solo muestra los Activos), con
-- disponibilidad "Bajo pedido" y la etiqueta "Por encargo". "commissionId" guarda de qué encargo
-- viene, para no crearlo dos veces. Si el encargo se borra, el producto se conserva.
-- =====================================================================

alter table "products"
    add column if not exists "commissionId" uuid references "commissions" ("commissionId") on delete set null;

create unique index if not exists products_commission_uidx
    on "products" ("commissionId") where "commissionId" is not null;
