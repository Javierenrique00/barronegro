#!/usr/bin/env bash
# Consultas de solo lectura a la API HTTP de Supabase con curl.
#
# No habla el protocolo de Postgres: usa PostgREST (/rest/v1), Auth (/auth/v1)
# y Storage (/storage/v1). Para SQL libre usa psql o el MCP de Supabase
# (ver supabase/README.md).
#
# Las credenciales se leen del entorno o de local.properties (no versionado):
#   supabase.url / supabase.anonKey
#
# Variables opcionales:
#   SUPABASE_URL, SUPABASE_ANON_KEY   sustituyen a local.properties
#   SUPABASE_TOKEN                    access_token ya emitido (JWT de usuario)
#   SUPABASE_EMAIL + SUPABASE_PASSWORD  para canjearlos por un token al vuelo
#
# Uso: ./supabase/consultar.sh <orden> [argumentos]
set -euo pipefail

RAIZ=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PROPIEDADES="$RAIZ/local.properties"
SEPARADOR=$'\n%{http_code}'

azul() { printf '\033[1;34m%s\033[0m\n' "$*"; }
rojo() { printf '\033[1;31m%s\033[0m\n' "$*" >&2; }
aviso() { printf '\033[1;33m%s\033[0m\n' "$*" >&2; }

uso() {
  cat <<'TEXTO'
Consultas de solo lectura a Supabase con curl.

Órdenes:
  salud                          Comprueba Auth y Storage (no requiere usuario).
  tabla <nombre> [filtro]        GET /rest/v1/<nombre>?<filtro>  (por defecto select=*&limit=20).
  rpc <función> [json]           POST /rest/v1/rpc/<función> con el cuerpo JSON indicado.
  storage                        Versión de Storage y buckets visibles para la clave actual.
  openapi                        GET /rest/v1/ (solo responde con service_role).
  login                          Canjea SUPABASE_EMAIL/SUPABASE_PASSWORD e imprime el access_token.
  sql                            Recuerda cómo ejecutar SQL de verdad (curl no puede).

Ejemplos:
  ./supabase/consultar.sh salud
  ./supabase/consultar.sh login
  SUPABASE_TOKEN=$TOKEN ./supabase/consultar.sh tabla salas 'select=id,nombre&limit=5'
  SUPABASE_TOKEN=$TOKEN ./supabase/consultar.sh rpc agenda_admin '{"p_dias":30}'

Sin SUPABASE_TOKEN ni SUPABASE_EMAIL/PASSWORD las órdenes de datos usan la clave
anon, que en este proyecto no tiene SELECT sobre ninguna tabla: verás un 401
"permission denied". Eso es el comportamiento esperado (RLS + privilegios mínimos).
TEXTO
}

valor_propiedad() {
  local clave=$1 fichero=$2 linea
  [ -f "$fichero" ] || return 1
  linea=$(grep -m1 -E "^[[:space:]]*${clave}[[:space:]]*=" "$fichero" || true)
  [ -n "$linea" ] || return 1
  printf '%s' "${linea#*=}" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'
}

rol_clave() {
  python3 - "$1" <<'PY' 2>/dev/null || true
import base64, json, sys
partes = sys.argv[1].split(".")
if len(partes) < 2:
    print("")
    raise SystemExit
try:
    relleno = "=" * (-len(partes[1]) % 4)
    datos = json.loads(base64.urlsafe_b64decode(partes[1] + relleno))
    print(datos.get("role", "") or "")
except Exception:
    print("")
PY
}

cargar_configuracion() {
  URL=${SUPABASE_URL:-}
  [ -n "$URL" ] || URL=$(valor_propiedad supabase.url "$PROPIEDADES" || true)
  CLAVE=${SUPABASE_ANON_KEY:-}
  [ -n "$CLAVE" ] || CLAVE=$(valor_propiedad supabase.anonKey "$PROPIEDADES" || true)

  if [ -z "$URL" ] || [ -z "$CLAVE" ]; then
    rojo "Faltan credenciales: define supabase.url y supabase.anonKey en local.properties"
    rojo "o exporta SUPABASE_URL y SUPABASE_ANON_KEY."
    exit 1
  fi

  URL=${URL%/}

  if [ "$(rol_clave "$CLAVE")" = "service_role" ] \
    || printf '%s' "$CLAVE" | grep -q 'c2VydmljZV9yb2xl'; then
    rojo "La clave configurada es service_role: omite RLS y no debe usarse en un cliente"
    rojo "ni guardarse en el repositorio. Usa la clave anon (pública)."
    exit 1
  fi
}


formatear() {
  local cuerpo
  cuerpo=$(cat)
  if command -v jq >/dev/null 2>&1 && printf '%s' "$cuerpo" | jq -e . >/dev/null 2>&1; then
    printf '%s' "$cuerpo" | jq .
  elif command -v python3 >/dev/null 2>&1 \
    && printf '%s' "$cuerpo" | python3 -m json.tool >/dev/null 2>&1; then
    printf '%s' "$cuerpo" | python3 -m json.tool
  else
    printf '%s\n' "$cuerpo"
  fi
}

json_campo() {
  local cuerpo=$1 campo=$2
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$cuerpo" | jq -r --arg c "$campo" '.[$c] // empty' 2>/dev/null || true
  elif command -v python3 >/dev/null 2>&1; then
    printf '%s' "$cuerpo" | python3 -c '
import json, sys
try:
    valor = json.load(sys.stdin).get(sys.argv[1])
except Exception:
    valor = None
print(valor if isinstance(valor, str) else "")
' "$campo" 2>/dev/null || true
  fi
}

json_credenciales() {
  if command -v jq >/dev/null 2>&1; then
    jq -nc --arg email "$1" --arg password "$2" '{email: $email, password: $password}'
  else
    python3 -c 'import json, sys; print(json.dumps({"email": sys.argv[1], "password": sys.argv[2]}))' "$1" "$2"
  fi
}

# peticion <método> <url> [cuerpo-json] [token]
# Deja el resultado en $CODIGO y $CUERPO.
peticion() {
  local metodo=$1 destino=$2 datos=${3:-} token=${4:-}
  local -a args=(-sS -X "$metodo" -w "$SEPARADOR" -H "apikey: $CLAVE")
  if [ -n "$token" ]; then
    args+=(-H "Authorization: Bearer $token")
  fi
  if [ -n "$datos" ]; then
    args+=(-H 'Content-Type: application/json' --data "$datos")
  fi

  local crudo
  if ! crudo=$(curl "${args[@]}" "$destino"); then
    rojo "No se pudo conectar con $destino"
    return 1
  fi
  CODIGO=${crudo##*$'\n'}
  CUERPO=${crudo%$'\n'*}
}

exigir_exito() {
  if [ "$CODIGO" -ge 400 ] 2>/dev/null; then
    rojo "HTTP $CODIGO"
    printf '%s\n' "$CUERPO" | formatear >&2
    return 1
  fi
}

canjear_credenciales() {
  local correo=$1 contrasena=$2
  if ! peticion POST "$URL/auth/v1/token?grant_type=password" \
    "$(json_credenciales "$correo" "$contrasena")" "$CLAVE"; then
    return 1
  fi
  if [ "$CODIGO" -ge 400 ] 2>/dev/null; then
    rojo "No se pudo iniciar sesión (HTTP $CODIGO):"
    printf '%s\n' "$CUERPO" | formatear >&2
    return 1
  fi
  local token
  token=$(json_campo "$CUERPO" access_token)
  if [ -z "$token" ]; then
    rojo "La respuesta de Auth no traía access_token."
    return 1
  fi
  printf '%s' "$token"
}

obtener_token() {
  local token=${SUPABASE_TOKEN:-}
  if [ -n "$token" ]; then
    printf '%s' "$token"
    return 0
  fi
  if [ -n "${SUPABASE_EMAIL:-}" ] && [ -n "${SUPABASE_PASSWORD:-}" ]; then
    canjear_credenciales "$SUPABASE_EMAIL" "$SUPABASE_PASSWORD"
    return
  fi
  printf ''
}

orden_salud() {
  azul "Auth · /auth/v1/health"
  peticion GET "$URL/auth/v1/health" "" "$CLAVE" || return 1
  exigir_exito || return 1
  printf '%s\n' "$CUERPO" | formatear

  azul "Storage · /storage/v1/version"
  peticion GET "$URL/storage/v1/version" "" "$CLAVE" || return 1
  exigir_exito || return 1
  printf '%s\n' "$CUERPO" | formatear

  azul "Auth · /auth/v1/settings"
  peticion GET "$URL/auth/v1/settings" "" "$CLAVE" || return 1
  exigir_exito || return 1
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$CUERPO" | jq '{email: .external.email, registro_abierto: (.disable_signup | not), confirmar_correo: (.mailer_autoconfirm | not)}'
  else
    printf '%s\n' "$CUERPO" | formatear
  fi
}

orden_tabla() {
  local tabla=${1:-}
  if [ -z "$tabla" ]; then
    rojo "Falta el nombre de la tabla."
    uso >&2
    exit 64
  fi
  local filtro=${2:-select=*&limit=20}
  local token
  token=$(obtener_token)
  if [ -z "$token" ]; then
    aviso "Sin sesión de usuario: se envía la clave anon, que no tiene SELECT en este proyecto."
    aviso "Exporta SUPABASE_EMAIL y SUPABASE_PASSWORD (o SUPABASE_TOKEN) para leer como usuario."
    token=$CLAVE
  fi

  azul "GET /rest/v1/$tabla?$filtro"
  peticion GET "$URL/rest/v1/$tabla?$filtro" "" "$token" || return 1
  exigir_exito || return 1
  printf '%s\n' "$CUERPO" | formatear
}

orden_rpc() {
  local funcion=${1:-} datos=${2:-'{}'}
  if [ -z "$funcion" ]; then
    rojo "Falta el nombre de la función."
    uso >&2
    exit 64
  fi
  local token
  token=$(obtener_token)
  if [ -z "$token" ]; then
    aviso "Sin sesión de usuario: se envía la clave anon; las RPC solo están concedidas a authenticated."
    aviso "Exporta SUPABASE_EMAIL y SUPABASE_PASSWORD (o SUPABASE_TOKEN) para invocarlas."
    token=$CLAVE
  fi

  azul "POST /rest/v1/rpc/$funcion"
  peticion POST "$URL/rest/v1/rpc/$funcion" "$datos" "$token" || return 1
  exigir_exito || return 1
  printf '%s\n' "$CUERPO" | formatear
}

orden_storage() {
  azul "Storage · versión"
  peticion GET "$URL/storage/v1/version" "" "$CLAVE" || return 1
  exigir_exito || return 1
  printf '%s\n' "$CUERPO" | formatear

  azul "Storage · buckets visibles"
  peticion GET "$URL/storage/v1/bucket" "" "$CLAVE" || return 1
  exigir_exito || return 1
  printf '%s\n' "$CUERPO" | formatear
}

orden_openapi() {
  aviso "El índice OpenAPI de PostgREST solo responde a service_role; con anon devuelve 401."
  azul "GET /rest/v1/"
  peticion GET "$URL/rest/v1/" "" "$CLAVE" || return 1
  exigir_exito || return 1
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$CUERPO" | jq -r '.paths | keys[]' | sort
  else
    printf '%s\n' "$CUERPO" | formatear
  fi
}

orden_login() {
  local correo=${SUPABASE_EMAIL:-} contrasena=${SUPABASE_PASSWORD:-}
  if [ -z "$correo" ] || [ -z "$contrasena" ]; then
    rojo "Define SUPABASE_EMAIL y SUPABASE_PASSWORD antes de pedir el token."
    rojo "  export SUPABASE_EMAIL='...' SUPABASE_PASSWORD='...'"
    exit 64
  fi
  local token
  token=$(canjear_credenciales "$correo" "$contrasena") || exit 1
  azul "access_token (caduca en ~1 h; trátalo como una contraseña):" >&2
  printf '%s\n' "$token"
}

orden_sql() {
  cat <<'TEXTO'
curl no habla el protocolo de Postgres, así que no puede ejecutar SQL libre.
Para consultas reales usa una de estas vías:

1) MCP de Supabase (ya conectado en el editor):
     execute_sql / list_tables / get_advisors

2) psql con la cadena de conexión del panel
   (Project Settings → Database → Connection string):
     psql "$CADENA" -c "select count(*) from public.reservas;"

3) supabase CLI para migraciones y diferencias:
     supabase db diff --linked

Lo que sí valida este script es la capa HTTP: Auth, PostgREST (tablas y RPC) y Storage.
TEXTO
}

principal() {
  local orden=${1:-}
  [ $# -gt 0 ] && shift || true

  case "$orden" in
    ""|-h|--help|ayuda) uso ;;
    salud) cargar_configuracion; orden_salud ;;
    tabla) cargar_configuracion; orden_tabla "$@" ;;
    rpc) cargar_configuracion; orden_rpc "$@" ;;
    storage) cargar_configuracion; orden_storage ;;
    openapi) cargar_configuracion; orden_openapi ;;
    login) cargar_configuracion; orden_login ;;
    sql) orden_sql ;;
    *)
      rojo "Orden desconocida: $orden"
      uso >&2
      exit 64
      ;;
  esac
}

principal "$@"

