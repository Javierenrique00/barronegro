-- 0007 · Disponibilidad semanal tipo Calendly y reserva directa
--
-- Cambia el modelo de "el administrador crea cada franja a mano" por una plantilla semanal:
-- el administrador publica una vez el horario de cada día de la semana y el sistema genera
-- todas las franjas de los próximos días. La reserva queda aprobada al instante (configurable).
-- Las reglas siguen viviendo solo en Postgres: el cliente nunca calcula disponibilidad.

-- Política de reservas por sala ------------------------------------------------
alter table public.salas
  add column duracion_turno_min    int     not null default 60
    check (duracion_turno_min between 15 and 240),
  add column duracion_maxima_min   int     not null default 120
    check (duracion_maxima_min >= duracion_turno_min and duracion_maxima_min <= 480),
  add column buffer_min            int     not null default 0
    check (buffer_min between 0 and 240),
  add column antelacion_min_horas  int     not null default 0
    check (antelacion_min_horas between 0 and 8760),
  add column dias_max_antelacion   int     not null default 60
    check (dias_max_antelacion between 1 and 365),
  add column aprobacion_automatica boolean not null default true,
  add column max_reservas_por_dia  int     not null default 1
    check (max_reservas_por_dia between 0 and 24);

-- Plantilla semanal ------------------------------------------------------------
create table public.disponibilidad_reglas (
  id uuid primary key default gen_random_uuid(),
  sala_id uuid not null references public.salas (id) on delete cascade,
  -- ISO: 1 = lunes … 7 = domingo, igual que Formato.diaSemana del cliente.
  dia_semana smallint not null check (dia_semana between 1 and 7),
  hora_inicio time not null,
  hora_fin time not null,
  creado_en timestamptz not null default now(),
  constraint disponibilidad_rango check (hora_fin > hora_inicio),
  constraint disponibilidad_unica unique (sala_id, dia_semana, hora_inicio)
);
create index disponibilidad_reglas_sala_idx on public.disponibilidad_reglas (sala_id, dia_semana);

alter table public.disponibilidad_reglas enable row level security;
revoke all on public.disponibilidad_reglas from public, anon, authenticated;
grant select on public.disponibilidad_reglas to authenticated;

drop policy if exists disponibilidad_lectura on public.disponibilidad_reglas;
create policy disponibilidad_lectura on public.disponibilidad_reglas
  for select to authenticated using (true);

-- Una reserva ya no necesita una fila de horarios pre-creada --------------------
alter table public.reservas alter column horario_id drop not null;

-- Cupos libres de una franja ----------------------------------------------------
-- Capacidad de la sala (o del horario puntual) menos las reservas que solapan,
-- ampliando cada reserva existente con el buffer configurado.
create or replace function privado.cupos_libres(
  p_sala_id uuid,
  p_inicio timestamptz,
  p_fin timestamptz,
  p_excluir uuid default null,
  p_capacidad int default null
) returns int
language sql stable security definer set search_path = public as $$
  with sala as (
    select greatest(coalesce(p_capacidad, s.capacidad_por_turno, 1), 1) as capacidad,
           make_interval(mins => coalesce(s.buffer_min, 0)) as buffer
    from public.salas s
    where s.id = p_sala_id
  ),
  ocupadas as (
    select count(*) as total
    from public.reservas r
    cross join sala
    where r.sala_id = p_sala_id
      and r.estado in ('pendiente', 'aprobada')
      and (p_excluir is null or r.id <> p_excluir)
      and tstzrange(
            public.a_timestamptz(r.sala_id, r.fecha, r.hora_inicio) - sala.buffer,
            public.a_timestamptz(r.sala_id, r.fecha, r.hora_fin) + sala.buffer
          ) && tstzrange(p_inicio, p_fin)
  )
  select greatest(
           coalesce((select capacidad from sala), 1) - coalesce((select total from ocupadas), 0),
           0);
$$;

-- Validación de franja (misma firma que la versión anterior) --------------------
-- Añade la comprobación de la plantilla semanal cuando la reserva no cuelga de un
-- horario puntual y aplica el buffer en el conteo de solapes.
create or replace function privado.validar_franja(
  p_sala_id uuid,
  p_horario_id uuid,
  p_fecha date,
  p_hora_inicio time,
  p_hora_fin time,
  p_reserva_id uuid default null
) returns void
language plpgsql stable security definer set search_path = public as $$
declare
  v_sala public.salas;
  v_inicio timestamptz;
  v_fin timestamptz;
  v_capacidad int;
  v_paso int;
begin
  if p_hora_fin <= p_hora_inicio then
    raise exception 'La hora final debe ser posterior a la hora inicial';
  end if;

  select * into v_sala from public.salas s where s.id = p_sala_id;
  if not found then
    raise exception 'La sala no existe';
  end if;

  v_inicio := public.a_timestamptz(p_sala_id, p_fecha, p_hora_inicio);
  v_fin := public.a_timestamptz(p_sala_id, p_fecha, p_hora_fin);

  if v_inicio <= now() then
    raise exception 'Esa franja ya pasó, elige una fecha futura';
  end if;

  if exists (
    select 1 from public.bloqueos_sala b
    where b.sala_id = p_sala_id
      and tstzrange(b.desde, b.hasta) && tstzrange(v_inicio, v_fin)
  ) then
    raise exception 'La sala está bloqueada en esa franja';
  end if;

  -- Los horarios puntuales (crear_horarios) ya definen su propia ventana.
  if p_horario_id is null then
    v_paso := greatest(coalesce(v_sala.duracion_turno_min, 60), 15);
    if not exists (
      select 1
      from public.disponibilidad_reglas r
      where r.sala_id = p_sala_id
        and r.dia_semana = extract(isodow from p_fecha)::int
        and p_hora_inicio >= r.hora_inicio
        and p_hora_fin <= r.hora_fin
        and mod(extract(epoch from (p_hora_inicio - r.hora_inicio)) / 60, v_paso) = 0
    ) then
      raise exception 'Ese horario no está dentro del horario de atención del taller';
    end if;
  end if;

  v_capacidad := coalesce(
    (select h.capacidad from public.horarios h where h.id = p_horario_id),
    v_sala.capacidad_por_turno,
    1);

  if privado.cupos_libres(p_sala_id, v_inicio, v_fin, p_reserva_id, v_capacidad) <= 0 then
    raise exception 'Esa franja ya está completa';
  end if;
end;
$$;


-- Franjas disponibles para un rango de días ------------------------------------
create or replace function public.franjas_disponibles(
  p_sala_id uuid,
  p_desde date default current_date,
  p_hasta date default current_date + 31
) returns table (
  fecha date,
  hora_inicio time,
  hora_fin time,
  hora_fin_maxima time,
  cupos_libres int
)
language plpgsql stable security definer set search_path = public as $$
declare
  v_sala public.salas;
  v_dia date;
  v_hoy date;
  v_regla record;
  v_hora time;
  v_fin time;
  v_inicio timestamptz;
  v_fin_ts timestamptz;
  v_paso int;
  v_libres int;
  v_minimo timestamptz;
  v_desde date;
  v_hasta date;
begin
  select * into v_sala from public.salas s where s.id = p_sala_id and s.activa;
  if not found then
    return;
  end if;

  v_paso := greatest(coalesce(v_sala.duracion_turno_min, 60), 15);
  v_minimo := now() + make_interval(hours => coalesce(v_sala.antelacion_min_horas, 0));
  -- "Hoy" se calcula en la zona del taller: con la fecha UTC, entre las 19:00 y la
  -- medianoche de Bogotá el rango del mes visible quedaría vacío.
  v_hoy := (now() at time zone v_sala.zona_horaria)::date;
  v_desde := greatest(coalesce(p_desde, v_hoy), v_hoy);
  v_hasta := least(
    coalesce(p_hasta, v_hoy + 31),
    v_hoy + coalesce(v_sala.dias_max_antelacion, 60));

  for v_dia in
    select g::date
    from generate_series(v_desde::timestamp, v_hasta::timestamp, interval '1 day') as g
  loop
    for v_regla in
      select r.hora_inicio, r.hora_fin
      from public.disponibilidad_reglas r
      where r.sala_id = p_sala_id
        and r.dia_semana = extract(isodow from v_dia)::int
      order by r.hora_inicio
    loop
      v_hora := v_regla.hora_inicio;
      while v_hora + make_interval(mins => v_paso) <= v_regla.hora_fin loop
        v_fin := v_hora + make_interval(mins => v_paso);
        v_inicio := public.a_timestamptz(p_sala_id, v_dia, v_hora);
        v_fin_ts := public.a_timestamptz(p_sala_id, v_dia, v_fin);

        if v_inicio >= v_minimo and not exists (
          select 1
          from public.bloqueos_sala b
          where b.sala_id = p_sala_id
            and tstzrange(b.desde, b.hasta) && tstzrange(v_inicio, v_fin_ts)
        ) then
          v_libres := privado.cupos_libres(p_sala_id, v_inicio, v_fin_ts);
          if v_libres > 0 then
            fecha := v_dia;
            hora_inicio := v_hora;
            hora_fin := v_fin;
            hora_fin_maxima := v_regla.hora_fin;
            cupos_libres := v_libres;
            return next;
          end if;
        end if;

        v_hora := v_fin;
      end loop;
    end loop;
  end loop;

  -- Franjas puntuales publicadas con crear_horarios(). Se omiten las que ya genera
  -- la plantilla para no duplicar horas en el calendario.
  return query
  select h.fecha, h.hora_inicio, h.hora_fin, h.hora_fin, c.cupos
  from public.horarios h
  cross join lateral (
    select privado.cupos_libres(
             p_sala_id,
             public.a_timestamptz(p_sala_id, h.fecha, h.hora_inicio),
             public.a_timestamptz(p_sala_id, h.fecha, h.hora_fin),
             null,
             h.capacidad) as cupos
  ) c
  where h.sala_id = p_sala_id
    and h.estado = 'disponible'
    and h.fecha between v_desde and v_hasta
    and public.a_timestamptz(p_sala_id, h.fecha, h.hora_inicio) >= v_minimo
    and c.cupos > 0
    and not exists (
      select 1
      from public.disponibilidad_reglas r
      where r.sala_id = h.sala_id
        and r.dia_semana = extract(isodow from h.fecha)::int
        and h.hora_inicio >= r.hora_inicio
        and h.hora_inicio < r.hora_fin
        and mod(extract(epoch from (h.hora_inicio - r.hora_inicio)) / 60, v_paso) = 0
    )
  order by h.fecha, h.hora_inicio;
end;
$$;


-- Reserva directa (confirmada al instante si la sala lo permite) -----------------
create or replace function public.reservar_franja(
  p_sala_id uuid,
  p_fecha date,
  p_hora_inicio time,
  p_duracion_min int default null,
  p_comentario text default null,
  p_token_humano text default null
) returns uuid
language plpgsql security definer set search_path = public, privado as $$
declare
  v_uid uuid := auth.uid();
  v_sala public.salas;
  v_duracion int;
  v_fin time;
  v_hoy date;
  v_horario_id uuid;
  v_token public.tokens_humanos;
  v_estado public.estado_reserva;
  v_del_dia int;
  v_id uuid;
begin
  if v_uid is null then
    raise exception 'Debes iniciar sesión para reservar' using errcode = '28000';
  end if;

  if not exists (select 1 from public.perfiles p where p.id = v_uid and p.estado = 'activo') then
    raise exception 'Tu usuario está bloqueado, contacta al administrador' using errcode = '42501';
  end if;

  select * into v_sala from public.salas s where s.id = p_sala_id;
  if not found or not v_sala.activa then
    raise exception 'La sala no existe';
  end if;
  if not v_sala.calendario_abierto then
    raise exception 'El calendario del taller está cerrado';
  end if;
  if p_hora_inicio is null then
    raise exception 'Elige una hora de inicio';
  end if;

  v_duracion := coalesce(p_duracion_min, v_sala.duracion_turno_min);
  if v_duracion < v_sala.duracion_turno_min or v_duracion > v_sala.duracion_maxima_min then
    raise exception 'La duración debe estar entre % y % minutos',
      v_sala.duracion_turno_min, v_sala.duracion_maxima_min;
  end if;
  if mod(v_duracion, v_sala.duracion_turno_min) <> 0 then
    raise exception 'La duración debe ser múltiplo de % minutos', v_sala.duracion_turno_min;
  end if;

  v_fin := p_hora_inicio + make_interval(mins => v_duracion);
  if v_fin <= p_hora_inicio then
    raise exception 'La reserva no puede terminar al día siguiente';
  end if;

  -- El horizonte se mide con la fecha local del taller, no con la UTC.
  v_hoy := (now() at time zone v_sala.zona_horaria)::date;
  if p_fecha > v_hoy + coalesce(v_sala.dias_max_antelacion, 60) then
    raise exception 'Solo puedes reservar hasta % días de antelación',
      coalesce(v_sala.dias_max_antelacion, 60);
  end if;

  -- Serializa las reservas de la misma sala: sin esto dos personas podrían ver el
  -- mismo cupo libre y ambas insertar a la vez.
  perform pg_advisory_xact_lock(hashtextextended(p_sala_id::text, 0));

  -- Si la franja cae dentro de un horario puntual, la reserva se cuelga de él para
  -- conservar su capacidad y su ventana.
  select h.id into v_horario_id
  from public.horarios h
  where h.sala_id = p_sala_id
    and h.fecha = p_fecha
    and h.estado = 'disponible'
    and h.hora_inicio <= p_hora_inicio
    and h.hora_fin >= v_fin
  order by h.hora_inicio desc
  limit 1;

  perform privado.validar_franja(p_sala_id, v_horario_id, p_fecha, p_hora_inicio, v_fin, null);

  if coalesce(v_sala.max_reservas_por_dia, 0) > 0 then
    select count(*) into v_del_dia
    from public.reservas r
    where r.usuario_id = v_uid
      and r.fecha = p_fecha
      and r.estado in ('pendiente', 'aprobada');

    if v_del_dia >= v_sala.max_reservas_por_dia then
      raise exception 'Ya tienes el máximo de reservas para ese día';
    end if;
  end if;

  if p_token_humano is null or length(p_token_humano) < 32 then
    raise exception 'Falta la verificación humana' using errcode = '42501';
  end if;

  select * into v_token
  from public.tokens_humanos t
  where t.token_hash = privado.hash(p_token_humano)
  for update;

  if not found or v_token.usado_en is not null
     or v_token.expira_en < now() or v_token.usuario_id <> v_uid then
    raise exception 'La verificación humana no es válida o expiró' using errcode = '42501';
  end if;

  update public.tokens_humanos set usado_en = now() where id = v_token.id;

  v_estado := case
    when coalesce(v_sala.aprobacion_automatica, true) then 'aprobada'::public.estado_reserva
    else 'pendiente'::public.estado_reserva
  end;

  insert into public.reservas (
    sala_id, horario_id, usuario_id, fecha, hora_inicio, hora_fin, estado,
    comentario_usuario, token_humano_id, resuelto_por, resuelto_en
  ) values (
    p_sala_id, v_horario_id, v_uid, p_fecha, p_hora_inicio, v_fin, v_estado,
    nullif(trim(coalesce(p_comentario, '')), ''), v_token.id,
    case when v_estado = 'aprobada' then v_uid end,
    case when v_estado = 'aprobada' then now() end
  ) returning id into v_id;

  perform privado.registrar_evento(
    v_id,
    case when v_estado = 'aprobada' then 'aprobada'::public.accion_reserva
         else 'solicitada'::public.accion_reserva end,
    jsonb_build_object(
      'fecha', p_fecha,
      'hora_inicio', p_hora_inicio,
      'hora_fin', v_fin,
      'duracion_min', v_duracion,
      'automatica', v_estado = 'aprobada'));

  return v_id;
end;
$$;


-- Administración de la plantilla y de la política -------------------------------

create or replace function public.guardar_disponibilidad(p_sala_id uuid, p_reglas jsonb)
returns int
language plpgsql security definer set search_path = public as $$
declare
  v_total int := 0;
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede cambiar el horario del taller' using errcode = '42501';
  end if;
  if not exists (select 1 from public.salas s where s.id = p_sala_id) then
    raise exception 'La sala no existe';
  end if;
  if p_reglas is null or jsonb_typeof(p_reglas) <> 'array' then
    raise exception 'Las reglas deben enviarse como una lista';
  end if;

  delete from public.disponibilidad_reglas where sala_id = p_sala_id;

  insert into public.disponibilidad_reglas (sala_id, dia_semana, hora_inicio, hora_fin)
  select p_sala_id, r.dia_semana, r.hora_inicio, r.hora_fin
  from jsonb_to_recordset(p_reglas) as r(dia_semana smallint, hora_inicio time, hora_fin time)
  where r.dia_semana between 1 and 7
    and r.hora_inicio is not null
    and r.hora_fin is not null
  order by r.dia_semana, r.hora_inicio;

  get diagnostics v_total = row_count;

  if exists (
    select 1
    from public.disponibilidad_reglas a
    join public.disponibilidad_reglas b
      on b.sala_id = a.sala_id
     and b.dia_semana = a.dia_semana
     and b.id <> a.id
     and b.hora_inicio < a.hora_fin
     and a.hora_inicio < b.hora_fin
    where a.sala_id = p_sala_id
  ) then
    raise exception 'Los horarios del mismo día no pueden solaparse';
  end if;

  return v_total;
end;
$$;

create or replace function public.actualizar_politica_sala(
  p_sala_id uuid,
  p_duracion_turno_min int,
  p_duracion_maxima_min int,
  p_buffer_min int,
  p_antelacion_min_horas int,
  p_dias_max_antelacion int,
  p_capacidad_por_turno int,
  p_aprobacion_automatica boolean,
  p_max_reservas_por_dia int
) returns public.salas
language plpgsql security definer set search_path = public as $$
declare
  v public.salas;
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede cambiar la política de reservas' using errcode = '42501';
  end if;
  if p_duracion_maxima_min < p_duracion_turno_min then
    raise exception 'La duración máxima no puede ser menor que la duración de cada turno';
  end if;

  update public.salas s
     set duracion_turno_min = p_duracion_turno_min,
         duracion_maxima_min = p_duracion_maxima_min,
         buffer_min = p_buffer_min,
         antelacion_min_horas = p_antelacion_min_horas,
         dias_max_antelacion = p_dias_max_antelacion,
         capacidad_por_turno = p_capacidad_por_turno,
         aprobacion_automatica = p_aprobacion_automatica,
         max_reservas_por_dia = p_max_reservas_por_dia
   where s.id = p_sala_id
  returning * into v;

  if v.id is null then
    raise exception 'La sala no existe';
  end if;

  return v;
end;
$$;

-- Agenda del administrador: reservas próximas con el nombre del usuario ---------
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
  creado_en timestamptz
)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede ver la agenda' using errcode = '42501';
  end if;

  return query
  select r.id, r.fecha, r.hora_inicio, r.hora_fin, r.estado,
         r.usuario_id, p.nombre_completo, p.telefono, r.comentario_usuario, r.creado_en
  from public.reservas r
  join public.perfiles p on p.id = r.usuario_id
  where r.fecha >= current_date
    and r.fecha <= current_date + greatest(coalesce(p_dias, 60), 1)
  order by r.fecha, r.hora_inicio;
end;
$$;

-- Privilegios ------------------------------------------------------------------
revoke all on function public.franjas_disponibles(uuid, date, date) from public, anon;
grant execute on function public.franjas_disponibles(uuid, date, date) to authenticated;

revoke all on function public.reservar_franja(uuid, date, time, int, text, text) from public, anon;
grant execute on function public.reservar_franja(uuid, date, time, int, text, text) to authenticated;

revoke all on function public.guardar_disponibilidad(uuid, jsonb) from public, anon;
grant execute on function public.guardar_disponibilidad(uuid, jsonb) to authenticated;

revoke all on function public.actualizar_politica_sala(uuid, int, int, int, int, int, int, boolean, int)
  from public, anon;
grant execute on function public.actualizar_politica_sala(uuid, int, int, int, int, int, int, boolean, int)
  to authenticated;

revoke all on function public.agenda_admin(int) from public, anon;
grant execute on function public.agenda_admin(int) to authenticated;

