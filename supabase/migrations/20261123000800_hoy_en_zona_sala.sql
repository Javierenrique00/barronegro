-- 0008 · "Hoy" en la zona del taller y horizonte de reserva
--
-- Corrige dos huecos de 0007:
--
-- 1. El rango de días usaba `current_date` (UTC). En America/Bogotá, entre las 19:00 y
--    la medianoche la fecha UTC ya es la del día siguiente, así que el último día del
--    mes visible quedaba fuera del rango y el calendario aparecía vacío.
-- 2. `reservar_franja` no comprobaba `dias_max_antelacion`: llamando la RPC directamente
--    se podía reservar más allá del horizonte publicado en el calendario.
--
-- Ambas funciones se recrean con la misma firma; no hay cambios de esquema.

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
