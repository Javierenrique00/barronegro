-- 0017 · Reportes de reservas para el administrador
--
-- `reporte_reservas` devuelve las reservas de un rango de fechas con los datos que
-- necesita el reporte mensual: nombre, correo y teléfono de la persona, la fecha,
-- la hora y la duración en minutos.
--
-- El correo de una reserva hecha con cuenta vive en `auth.users` (no en `perfiles`),
-- por eso el left join; las reservas anónimas lo guardan en `correo_contacto`.
--
-- Reglas:
--   * Solo un administrador activo puede llamarla (la misma guarda que `agenda_admin`).
--   * El rango se valida aquí; nunca se confía en el cliente.
--   * Es `stable` y `security definer`: las tablas siguen cerradas por RLS.

create or replace function public.reporte_reservas(
  p_desde date,
  p_hasta date,
  p_sala_id uuid default null
)
returns table (
  reserva_id uuid,
  fecha date,
  hora_inicio time,
  hora_fin time,
  duracion_min int,
  estado public.estado_reserva,
  origen public.origen_reserva,
  nombre text,
  correo text,
  telefono text,
  comentario text,
  creado_en timestamptz
)
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede ver los reportes' using errcode = '42501';
  end if;

  if p_desde is null or p_hasta is null or p_hasta < p_desde then
    raise exception 'Rango de fechas inválido' using errcode = '22023';
  end if;

  -- Un año cubre cualquier reporte mensual o anual sin permitir consultas enormes.
  if p_hasta > p_desde + 366 then
    raise exception 'El rango máximo de un reporte es de un año' using errcode = '22023';
  end if;

  return query
  select r.id,
         r.fecha,
         r.hora_inicio,
         r.hora_fin,
         (extract(epoch from (r.hora_fin - r.hora_inicio)) / 60)::int,
         r.estado,
         r.origen,
         coalesce(
           nullif(trim(p.nombre_completo), ''),
           nullif(trim(r.nombre_contacto), ''),
           'Sin nombre'
         ) as nombre,
         coalesce(nullif(trim(r.correo_contacto), ''), u.email) as correo,
         coalesce(
           nullif(trim(p.telefono), ''),
           nullif(trim(r.telefono_contacto), '')
         ) as telefono,
         r.comentario_usuario,
         r.creado_en
  from public.reservas r
  left join public.perfiles p on p.id = r.usuario_id
  left join auth.users u on u.id = r.usuario_id
  where r.fecha between p_desde and p_hasta
    and (p_sala_id is null or r.sala_id = p_sala_id)
  order by r.fecha, r.hora_inicio;
end;
$$;

revoke all on function public.reporte_reservas(date, date, uuid) from public, anon;
grant execute on function public.reporte_reservas(date, date, uuid) to authenticated;

-- La agenda del panel también gana el correo de las reservas hechas con cuenta.
-- Se mantienen la firma y las columnas: `create or replace` es suficiente.
create or replace function public.agenda_admin(p_dias int default 60)
returns table (
  reserva_id uuid,
  fecha date,
  hora_inicio time,
  hora_fin time,
  estado public.estado_reserva,
  usuario_id uuid,
  nombre text,
  telefono text,
  comentario text,
  creado_en timestamptz,
  correo text,
  origen public.origen_reserva
)
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede ver la agenda' using errcode = '42501';
  end if;

  return query
  select r.id,
         r.fecha,
         r.hora_inicio,
         r.hora_fin,
         r.estado,
         r.usuario_id,
         coalesce(nullif(p.nombre_completo, ''), r.nombre_contacto, 'Sin nombre') as nombre,
         coalesce(p.telefono, r.telefono_contacto) as telefono,
         r.comentario_usuario,
         r.creado_en,
         coalesce(nullif(trim(r.correo_contacto), ''), u.email) as correo,
         r.origen
  from public.reservas r
  left join public.perfiles p on p.id = r.usuario_id
  left join auth.users u on u.id = r.usuario_id
  where r.fecha >= current_date
    and r.fecha <= current_date + greatest(coalesce(p_dias, 60), 1)
  order by r.fecha, r.hora_inicio;
end;
$$;
