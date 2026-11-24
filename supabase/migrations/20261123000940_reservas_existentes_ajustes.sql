
create or replace function public.responder_propuesta(p_reserva_id uuid, p_aceptar boolean)
returns public.reservas
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v public.reservas;
begin
  if auth.uid() is null then
    raise exception 'Debes iniciar sesión' using errcode = '28000';
  end if;

  select * into v from public.reservas where id = p_reserva_id for update;
  if not found then
    raise exception 'La reserva no existe';
  end if;
  if v.usuario_id is distinct from auth.uid() then
    raise exception 'Esta reserva no te pertenece' using errcode = '42501';
  end if;
  if v.estado <> 'reprogramada' then
    raise exception 'La reserva no tiene una propuesta pendiente';
  end if;

  if p_aceptar then
    perform privado.validar_franja(
      v.sala_id, v.horario_id, v.propuesta_fecha, v.propuesta_hora_inicio, v.propuesta_hora_fin, v.id);

    update public.reservas
       set fecha = v.propuesta_fecha,
           hora_inicio = v.propuesta_hora_inicio,
           hora_fin = v.propuesta_hora_fin,
           estado = 'aprobada',
           resuelto_por = auth.uid(),
           resuelto_en = now(),
           propuesta_fecha = null,
           propuesta_hora_inicio = null,
           propuesta_hora_fin = null
     where id = p_reserva_id
     returning * into v;

    perform privado.registrar_evento(p_reserva_id, 'propuesta_aceptada', '{}'::jsonb);
  else
    update public.reservas
       set estado = 'cancelada',
           comentario_usuario = coalesce(comentario_usuario, 'Propuesta rechazada por el usuario'),
           resuelto_por = auth.uid(),
           resuelto_en = now(),
           propuesta_fecha = null,
           propuesta_hora_inicio = null,
           propuesta_hora_fin = null
     where id = p_reserva_id
     returning * into v;

    perform privado.registrar_evento(p_reserva_id, 'propuesta_rechazada', '{}'::jsonb);
  end if;

  return v;
end;
$$;

create or replace function public.cancelar_reserva(p_reserva_id uuid)
returns public.reservas
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v public.reservas;
begin
  if auth.uid() is null then
    raise exception 'Debes iniciar sesión' using errcode = '28000';
  end if;

  select * into v from public.reservas where id = p_reserva_id for update;
  if not found then
    raise exception 'La reserva no existe';
  end if;
  if v.usuario_id is distinct from auth.uid() and not public.es_admin() then
    raise exception 'Esta reserva no te pertenece' using errcode = '42501';
  end if;
  if v.estado not in ('pendiente', 'reprogramada', 'aprobada') then
    raise exception 'La reserva ya está cerrada (estado: %)', v.estado;
  end if;

  update public.reservas
     set estado = 'cancelada',
         resuelto_por = auth.uid(),
         resuelto_en = now(),
         propuesta_fecha = null,
         propuesta_hora_inicio = null,
         propuesta_hora_fin = null
   where id = p_reserva_id
   returning * into v;

  perform privado.registrar_evento(p_reserva_id, 'cancelada', '{}'::jsonb);
  return v;
end;
$$;


create or replace function public.registrar_adjunto(p_reserva_id uuid, p_ruta text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_reserva public.reservas;
  v_id uuid;
begin
  if v_uid is null then
    raise exception 'Debes iniciar sesión' using errcode = '28000';
  end if;

  select * into v_reserva from public.reservas where id = p_reserva_id;
  if not found then
    raise exception 'La reserva no existe';
  end if;
  if v_reserva.usuario_id is distinct from v_uid and not public.es_admin() then
    raise exception 'Esta reserva no te pertenece' using errcode = '42501';
  end if;
  if p_ruta is null or position(v_uid::text in p_ruta) <> 1 then
    raise exception 'La ruta del adjunto debe empezar por tu identificador de usuario';
  end if;

  insert into public.adjuntos (reserva_id, ruta, subido_por)
  values (p_reserva_id, p_ruta, v_uid)
  returning id into v_id;

  return v_id;
end;
$$;

create or replace function public.proponer_reprogramacion(
  p_reserva_id uuid, p_fecha date, p_hora_inicio time, p_hora_fin time, p_motivo text default null
) returns public.reservas
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v public.reservas;
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede proponer una nueva fecha' using errcode = '42501';
  end if;

  select * into v from public.reservas where id = p_reserva_id for update;
  if not found then
    raise exception 'La reserva no existe';
  end if;
  if v.origen = 'anonima' then
    raise exception 'Las reservas anónimas no admiten propuestas; apruébalas o cancélalas directamente';
  end if;
  if v.estado not in ('pendiente', 'reprogramada') then
    raise exception 'Solo se reprograma una reserva pendiente (estado actual: %)', v.estado;
  end if;

  perform privado.validar_franja(v.sala_id, v.horario_id, p_fecha, p_hora_inicio, p_hora_fin, v.id);

  update public.reservas
     set estado = 'reprogramada',
         propuesta_fecha = p_fecha,
         propuesta_hora_inicio = p_hora_inicio,
         propuesta_hora_fin = p_hora_fin,
         comentario_admin = coalesce(p_motivo, comentario_admin),
         resuelto_por = null,
         resuelto_en = null
   where id = p_reserva_id
   returning * into v;

  perform privado.registrar_evento(
    p_reserva_id, 'reprogramada',
    jsonb_build_object('fecha', p_fecha, 'hora_inicio', p_hora_inicio, 'hora_fin', p_hora_fin,
                       'motivo', p_motivo));

  return v;
end;
$$;

