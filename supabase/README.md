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

## Mantener el proyecto despierto (Raspberry Pi)

El plan Free pausa un proyecto tras ~1 semana de baja actividad; según la documentación, *"a few
user requests to the database each day over the previous week is enough"* para que no lo pause.
`supabase/vigilante.sh` cubre eso y además avisa si el proyecto deja de responder.

Cada ejecución hace dos peticiones pequeñas y solo da el proyecto por vivo si **ambas** responden
`200`:

| Petición | Qué demuestra |
|---|---|
| `GET /auth/v1/health` | conectividad y que la clave publicable sigue siendo válida |
| `POST /rest/v1/rpc/sala_publica` con `SALA_ID` | lectura real en Postgres (actividad de BD, no solo Auth) |

Si algo falla reintenta 3 veces (20 s entre intentos) y entonces publica **un único aviso** en
`https://ntfy.sh/potito1111` con host, hora, endpoint, código HTTP o error de `curl`, y un enlace al
panel para reanudar el proyecto. El estado se guarda en `~/.cache/barronegro-vigilante/estado` para
no repetir el aviso en cada vuelta.

```bash
./supabase/vigilante.sh --simular      # comprueba y muestra el aviso sin enviarlo
./supabase/vigilante.sh --probar-ntfy  # envía un aviso de prueba
./supabase/vigilante.sh --forzar       # repite el aviso aunque ya constara como caído
```

En la Pi, cada 15 minutos:

```bash
chmod +x /home/pi/barronegro/supabase/vigilante.sh

# crontab -e
*/15 * * * * /home/pi/barronegro/supabase/vigilante.sh >> /home/pi/barronegro-vigilante.log 2>&1
```

Variables de entorno (todas opcionales): `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SALA_ID`,
`COMPROBAR_RPC=0` (solo health), `NTFY_URL`, `NTFY_TOKEN` (topic privado), `NTFY_TITULO`,
`NTFY_PRIORIDAD`, `NTFY_ETIQUETAS`, `INTENTOS`, `ESPERA`, `AVISAR_RECUPERACION=1` (avisa también al
volver), `DIR_ESTADO`, `PANEL_URL`. Salida: `0` bien · `1` caído o aviso no enviado · `64` uso ·
`65` configuración.

Tres avisos:

- **El topic de ntfy es público.** En ntfy.sh el nombre del topic *"is essentially a password"*:
  quien lo conozca puede suscribirse. Usa un nombre aleatorio o un topic protegido con `NTFY_TOKEN`.
- **Solo la clave publicable.** Es pública por diseño y la que usan la web y el APK; la `service_role`
  nunca va aquí (el script la rechaza).
- **El keep-alive reduce el riesgo, no lo elimina.** Un fallo del propio vigilante (Pi apagada, sin
  internet) puede acabar en pausa, y solo el plan Pro garantiza que no se pause. Si la Pi se queda
  sin red, el aviso lo delata con el error de `curl`: `Could not resolve host`,
  `Connection timed out`, `Operation timed out`.

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

## Reservas anónimas y correos

La `anon` key sigue sin poder leer tablas. El flujo anónimo solo ejecuta funciones concretas:

```bash
# sesión de navegador
curl -s -X POST "$URL/rest/v1/rpc/crear_sesion_anonima" \
  -H "apikey: $KEY" -H "Content-Type: application/json" -d '{}'

# calendario público
curl -s -X POST "$URL/rest/v1/rpc/sala_publica" \
  -H "apikey: $KEY" -H "Content-Type: application/json" \
  -d '{"p_sala_id":"00000000-0000-4000-8000-000000000001"}'

curl -s -X POST "$URL/rest/v1/rpc/franjas_disponibles" \
  -H "apikey: $KEY" -H "Content-Type: application/json" \
  -d '{"p_sala_id":"00000000-0000-4000-8000-000000000001","p_desde":"2026-12-01","p_hasta":"2026-12-07"}'
```

El resto (`crear_reto_humano_anonimo`, `verificar_reto_humano_anonimo`, `reservar_franja_anonima`,
`mis_reservas_anonimas`, `cancelar_reserva_anonima`) recibe `p_sesion`; es el valor de la cookie
`barronegro_sesion`. Sin él no se puede leer ni cancelar una reserva anónima. Las tablas
`reservas`, `retos_humanos`, `tokens_humanos` y `privado.correos_log` siguen denegadas para `anon`.

`agenda_admin` ahora devuelve también `correo` y `origen`; en las reservas anónimas el nombre y el
teléfono salen de los campos de contacto porque no hay fila en `perfiles`.

Los correos de reserva se disparan desde Postgres con `pg_net`. La API key se guarda en Vault y la
configuración no secreta en `privado.secretos`; revisa `privado.correos_log` y
`net._http_response` para diagnosticar un envío. El detalle está en el README principal.

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

