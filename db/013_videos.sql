-- =====================================================================
-- 013 - Video corto opcional en cada producto y en cada prenda de Colaboraciones
-- Ejecutar en Supabase > SQL Editor ANTES de subir videos desde el panel. Es repetible.
--
-- Se guarda solo la ruta del archivo MP4 en el Storage ("videoPath"); null = sin video.
-- La tienda empezará a mostrarlo cuando se actualicen las vistas públicas (paso aparte).
-- =====================================================================

alter table "products"     add column if not exists "videoPath" text;
alter table "collabPieces" add column if not exists "videoPath" text;
