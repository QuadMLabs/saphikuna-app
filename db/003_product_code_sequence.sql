-- =====================================================================
-- 003 - Código de producto secuencial (SPK-001, SPK-002, ...)
-- Ejecutar en Supabase > SQL Editor ANTES de crear productos nuevos.
-- Es repetible: no baja nunca el contador.
--
-- La aplicación pide el siguiente número a esta secuencia al CREAR un
-- producto. Una secuencia no repite números, ni siquiera si se borra el
-- último producto (así un código viejo de la tienda nunca se reutiliza).
-- Puede haber saltos si un guardado falla justo después de pedir el número.
-- =====================================================================

create sequence if not exists "productCodeSeq" start with 1 minvalue 1;

-- Deja el contador en el mayor número ya usado.
--
-- 20 = último código de la app de ventas (SPK-020). Si la app de ventas
-- tiene códigos más altos (por ejemplo SPK-021 al separar las bufandas),
-- cambia el 20 por ese número ANTES de ejecutar.
do $$
declare
    ultimo_tienda constant bigint := 20;
    ultimo_base   bigint;
    actual        bigint;
begin
    select coalesce(max(substring("code" from '^SPK-([0-9]+)$')::bigint), 0)
      into ultimo_base
      from "products"
     where "code" ~ '^SPK-[0-9]+$';

    select case when is_called then last_value else 0 end
      into actual
      from "productCodeSeq";

    perform setval('"productCodeSeq"', greatest(ultimo_tienda, ultimo_base, actual), true);
end $$;

-- Para ver cuál será el próximo código (no consume números):
-- select 'SPK-' || lpad((case when is_called then last_value + 1 else last_value end)::text, 3, '0')
--   from "productCodeSeq";
