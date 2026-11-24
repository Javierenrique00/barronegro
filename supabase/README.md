# Consultar la base de datos

Dos cosas distintas que conviene no confundir:

| Vía | Qué alcanza | Credencial |
|---|---|---|
| **API HTTP** (`curl`) | PostgREST (`/rest/v1/...`: tablas y RPC), Auth (`/auth/v1/...`), Storage (`/storage/v1/...`) | `anon` key **+ JWT de usuario** para leer datos |
| **SQL** (`psql`, MCP, `supabase db diff`) | Consultas libres, DDL, planes, RLS real | Connection string o token del MCP |

`curl` **no** habla el protocolo de Postgres: no hay endpoint para `select * from ...`. Lo que sí
permite es comprobar que la base responde, que las RPC existen y que RLS devuelve lo que debe.

## Credenciales

La `anon` key y la URL están en `local.properties` (no versionado):

```properties
supabase.url=https://qxsehofkuwmypuhjzult.supabase.co
supabase.anonKey=<clave pública>
```

La `anon` key es pública por diseño (viaja en el APK y en la web). **La `service_role` no**: omite
RLS por completo, así que ni en el repositorio, ni en `local.properties`, ni en el cliente.

> En este proyecto la `anon` **no puede leer ninguna tabla**. Las migraciones hacen
> `revoke all ... from public, anon, authenticated` y después conceden `SELECT` solo a
> `authenticated`; `20261123000500_endurecer_funciones.sql` revoca además `es_admin()` a `anon`.
> Un `401 permission denied` con la clave anon es el resultado correcto.

## Script de ayuda

`supabase/consultar.sh` envuelve las recetas de abajo (bash 3.2 + `curl`; usa `jq` o `python3`
para formatear si están disponibles):

```bash
./supabase/consultar.sh salud          # Auth + Storage + ajustes, sin usuario
./supabase/consultar.sh storage        # versión y buckets visibles
./supabase/consultar.sh login          # canjea SUPABASE_EMAIL/SUPABASE_PASSWORD → access_token
./supabase/consultar.sh tabla salas 'select=id,nombre&limit=5'
./supabase/consultar.sh rpc agenda_admin '{"p_dias":30}'
./supabase/consultar.sh openapi        # solo responde con service_role
./supabase/consultar.sh sql            # recuerda las vías para SQL real
```

Las credenciales se pasan por entorno, nunca por argumentos:

```bash
export SUPABASE_EMAIL='javierenrique@yahoo.com'
read -rs SUPABASE_PASSWORD && export SUPABASE_PASSWORD   # no queda en el historial
./supabase/consultar.sh login
export SUPABASE_TOKEN='eyJ...'                            # caduca en ~1 h
```

Rechaza arrancar si detecta una `service_role` en `SUPABASE_ANON_KEY`, y devuelve código de salida
distinto de cero cuando el HTTP es ≥ 400.

## Recetas con curl

### 1. ¿Está viva la base? (sin credenciales de usuario)

```bash
URL=$(grep -m1 '^supabase.url=' local.properties | cut -d= -f2-)
KEY=$(grep -m1 '^supabase.anonKey=' local.properties | cut -d= -f2-)

curl -s "$URL/auth/v1/health"    -H "apikey: $KEY"   # {"version":"v2.198.0","name":"GoTrue",...}
curl -s "$URL/storage/v1/version" -H "apikey: $KEY"  # 1.96.4
curl -s "$URL/auth/v1/settings"  -H "apikey: $KEY"   # proveedores, signup, confirmación de correo
```

### 2. Login y lectura de tablas

```bash
TOKEN=$(curl -s -X POST "$URL/auth/v1/token?grant_type=password" \
  -H "apikey: $KEY" -H "Content-Type: application/json" \
  -d '{"email":"...","password":"..."}' | jq -r .access_token)

curl -s "$URL/rest/v1/salas?select=id,nombre,zona_horaria,capacidad_por_turno,calendario_abierto,activa" \
  -H "apikey: $KEY" -H "Authorization: Bearer $TOKEN"
```

Filtros de PostgREST que se usan aquí: `select=`, `limit=`, `order=creado_en.desc`,
`estado=eq.pendiente`, `fecha=gte.2026-12-01`.

### 3. RPC (las reglas de negocio)

```bash
curl -s -X POST "$URL/rest/v1/rpc/franjas_disponibles" \
  -H "apikey: $KEY" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"p_sala_id":"<uuid>","p_desde":"2026-12-01","p_hasta":"2026-12-07"}'

curl -s -X POST "$URL/rest/v1/rpc/agenda_admin" \
  -H "apikey: $KEY" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"p_dias":60}'
```

El nombre y el orden de los argumentos deben coincidir con la migración: los parámetros son
`p_*` y las funciones sobrecargadas (`franjas_disponibles`, `reservar_franja`) resuelven por el
JSON enviado. Una RPC concedida solo a `authenticated` devuelve `401 permission denied for function`.

### 4. Errores que ya están verificados en este proyecto

| Petición con la clave `anon` | Respuesta |
|---|---|
| `GET /auth/v1/health` | `200` |
| `GET /storage/v1/version` | `200` |
| `GET /storage/v1/bucket` | `200 []` (RLS oculta los buckets al rol `anon`) |
| `GET /rest/v1/salas?select=id,nombre,slug` | `400 42703` · `column salas.slug does not exist` (PostgREST valida columnas antes que permisos) |
| `GET /rest/v1/salas?select=*` | `401 42501` · `permission denied for table salas` |
| `GET /rest/v1/reservas?...` | `401 42501` · `permission denied for table reservas` |
| `POST /rest/v1/rpc/es_admin` | `401 42501` · `permission denied for function es_admin` |
| `GET /rest/v1/` (índice OpenAPI) | `401` · `Only the service_role API key can be used for this endpoint` |
| `GET /rest/v1/salas` con un JWT inválido | `401 PGRST301` · `JWT cryptographic operation failed` |

Otros dos que aparecen al usar el login:

- `400 invalid_credentials`: correo o contraseña incorrectos (y cuenta para el límite de intentos).
- `400 email_not_confirmed`: el proyecto tiene `mailer_autoconfirm:false`, así que el usuario debe
  confirmar el correo antes de poder iniciar sesión.

### 5. Storage

```bash
curl -s "$URL/storage/v1/bucket" -H "apikey: $KEY" -H "Authorization: Bearer $TOKEN"

# Bucket público: la URL sirve el archivo sin token (solo si el objeto existe)
curl -sI "$URL/storage/v1/object/public/taller-fotos/<ruta>.jpg" -H "apikey: $KEY"
```

Buckets del proyecto: `avatares` (público), `taller-fotos` (público) y `reservas-adjuntos`
(privado, solo dueño o admin).

## SQL de verdad

Cuando lo que hace falta es `count(*)`, un `explain` o comprobar una política RLS:

1. **MCP de Supabase** (ya conectado en el editor): `execute_sql`, `list_tables`, `get_advisors`.
2. **`psql`** con la cadena de *Project Settings → Database → Connection string*:
   ```bash
   psql "$CADENA" -c "select estado, count(*) from public.reservas group by 1;"
   ```
3. **`supabase db diff --linked`** para ver el esquema real frente a `supabase/migrations/`.

Para que `curl` pudiera ejecutar SQL haría falta un endpoint propio (Edge Function o función
`security definer` que reciba la consulta), y eso es exactamente lo que no conviene exponer en un
backend que se apoya en RLS.

## Seguridad, en tres líneas

- `local.properties` está en `.gitignore`; no lo sustituyas por constantes en el código.
- El JWT de usuario caduca (~1 h) y equivale a una contraseña temporal: no lo pegues en issues ni en
  capturas.
- Si alguna vez se filtra la `service_role`, rótala en *Project Settings → API* de inmediato.

