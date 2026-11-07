# Saphikuna

Panel de administración de Saphikuna (tejidos artesanales). Es solo para administrar:
las ventas y compras están en la tienda en línea, que es otra aplicación.

## Qué hace

- **Productos:** crear, editar y eliminar, con fotos (WebP optimizado), colores, tallas, precio,
  descuento, disponibilidad y fecha "Nuevo hasta". Código `SPK-###` automático.
- **Catálogos:** colores (con su hex), tallas, categorías y colecciones.
- **Encargos:** obras por pedido para marcas, con fecha de entrega, estado, precio y adelanto.
  El menú muestra cuántas entregas son urgentes.
- **Inicio:** entregas, resumen, productos por completar y últimos modificados.
- **Configuración:** días de "nuevo", datos de la tienda y estado del sistema.

Pensado para usarse desde el celular y desde la laptop, con el mínimo de JavaScript.

## Tecnologías

Java 21, Spring Boot 4, Thymeleaf, JPA/Hibernate, PostgreSQL y Supabase Storage (fotos), Maven.

## Cómo ejecutarlo

```bash
./mvnw spring-boot:run
```

La configuración (base de datos y Supabase) está en `src/main/resources/application.properties`.

> Pendiente antes de publicar: las claves de Supabase están en ese archivo. Rotarlas y
> pasarlas a variables de entorno.

## Base de datos

Los cambios del esquema están en [`db/`](db). Se ejecutan en el SQL Editor de Supabase, **en orden**
(son repetibles): `001` a `005`.

## Pruebas

```bash
./mvnw test
```

## Diseño

El logo original está en [`design/`](design). La app usa `static/images/` (logo e iconos) y las
variables de color de `static/css/admin.css`: cambiar la marca es editar ese bloque.
