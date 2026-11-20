-- =====================================================================
-- 010 - Encargos: elegir una marca de Colaboraciones o registrar un cliente personal
-- Ejecutar en Supabase > SQL Editor DESPUÉS de 009 (usa la tabla "collaborations").
-- Es repetible (no borra ni cambia datos existentes).
--
--   - collaborationId : la marca elegida (null = cliente personal o encargo antiguo).
--                       Si la marca se borra, el encargo se conserva con el nombre que tenía.
--   - clientContact   : celular u otra referencia del cliente (opcional).
-- "clientName" sigue guardando el nombre que se muestra (el de la marca, o el del cliente).
-- =====================================================================

alter table "commissions"
    add column if not exists "collaborationId" uuid references "collaborations" ("collaborationId") on delete set null,
    add column if not exists "clientContact"   text;

create index if not exists commissions_collaboration_idx on "commissions" ("collaborationId");
