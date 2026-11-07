-- =====================================================================
-- 002 - Borrado en cascada desde products
-- Ejecutar en Supabase > SQL Editor. Es repetible y NO borra datos.
--
-- Recrea con ON DELETE CASCADE todas las claves foráneas que apuntan a
-- "products" (productImages, productCollections, productVariants si aún
-- existe, etc.). Así, borrar un producto nunca queda bloqueado por filas
-- hijas que la aplicación ya no conoce (por ejemplo, la tabla vieja
-- productVariants).
-- =====================================================================

begin;

do $$
declare
    r record;
begin
    for r in
        select c.conrelid::regclass as tbl,
               c.conname            as name,
               a.attname            as col
        from pg_constraint c
        join pg_attribute  a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
        where c.contype = 'f'
          and c.confrelid = '"products"'::regclass
          and array_length(c.conkey, 1) = 1
          and c.confdeltype <> 'c'            -- 'c' = CASCADE
    loop
        execute format('alter table %s drop constraint %I', r.tbl, r.name);
        execute format(
            'alter table %s add constraint %I foreign key (%I) references "products" ("producttId") on delete cascade',
            r.tbl, r.name, r.col);
        raise notice 'Cascade aplicado en % (%)', r.tbl, r.name;
    end loop;
end $$;

commit;

-- Para ver el resultado:
-- select conrelid::regclass as tabla, conname, pg_get_constraintdef(oid)
-- from pg_constraint where confrelid = '"products"'::regclass;
