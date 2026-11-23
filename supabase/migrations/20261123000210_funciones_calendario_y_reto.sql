-- Administración del calendario -------------------------------------------
create or replace function public.crear_horarios(
  p_sala_id uuid,
  p_fechas date[],
  p_hora_inicio time,
  p_hora_fin time,
  p_capacidad int default 1
) returns setof public.horarios
language plpgsql security definer set search_path = public as $$
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede crear horarios' using errcode = '42501';
  end if;
  if p_hora_fin <= p_hora_inicio then
    raise exception 'La hora final debe ser posterior a la hora inicial';
  end if;

  return query
  insert into public.horarios (sala_id, fecha, hora_inicio, hora_fin, capacidad, creado_por)
  select p_sala_id, f, p_hora_inicio, p_hora_fin, greatest(coalesce(p_capacidad, 1), 1), auth.uid()
  from unnest(p_fechas) as f
  where f >= current_date
  on conflict (sala_id, fecha, hora_inicio, hora_fin) do nothing
  returning *;
end;
$$;

create or replace function public.cambiar_estado_horario(p_horario_id uuid, p_estado public.estado_horario)
returns public.horarios
language plpgsql security definer set search_path = public as $$
declare
  v public.horarios;
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede cambiar horarios' using errcode = '42501';
  end if;

  update public.horarios set estado = p_estado where id = p_horario_id returning * into v;
  if v.id is null then
    raise exception 'El horario no existe';
  end if;
  return v;
end;
$$;

create or replace function public.bloquear_calendario(
  p_sala_id uuid, p_desde timestamptz, p_hasta timestamptz, p_motivo text default null
) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_id uuid;
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede bloquear el calendario' using errcode = '42501';
  end if;
  if p_hasta <= p_desde then
    raise exception 'El fin del bloqueo debe ser posterior al inicio';
  end if;

  insert into public.bloqueos_sala (sala_id, desde, hasta, motivo, creado_por)
  values (p_sala_id, p_desde, p_hasta, p_motivo, auth.uid())
  returning id into v_id;

  return v_id;
end;
$$;

create or replace function public.quitar_bloqueo(p_bloqueo_id uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede abrir el calendario' using errcode = '42501';
  end if;
  delete from public.bloqueos_sala where id = p_bloqueo_id;
end;
$$;

create or replace function public.cambiar_calendario_abierto(p_sala_id uuid, p_abierto boolean)
returns public.salas
language plpgsql security definer set search_path = public as $$
declare
  v public.salas;
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede abrir o cerrar el calendario' using errcode = '42501';
  end if;

  update public.salas set calendario_abierto = p_abierto where id = p_sala_id returning * into v;
  if v.id is null then
    raise exception 'La sala no existe';
  end if;
  return v;
end;
$$;

-- Verificación humana (reto propio, verificado en el servidor) -------------
create or replace function public.crear_reto_humano()
returns jsonb
language plpgsql security definer set search_path = public, privado as $$
declare
  v_uid uuid := auth.uid();
  v_total int;
  v_id uuid := gen_random_uuid();
  v_nonce text := encode(extensions.gen_random_bytes(8), 'hex');
  v_formas text[] := array['taza', 'plato', 'jarron', 'maceta'];
  v_colores text[] := array['#C2704B', '#5B7F6B', '#3E5C76', '#8C6A3F'];
  v_forma_obj text;
  v_franjas_obj int;
  v_correcta int;
  v_piezas jsonb := '[]'::jsonb;
  v_forma text;
  v_franjas int;
  v_i int;
begin
  if v_uid is null then
    raise exception 'Debes iniciar sesión para verificar que eres humano' using errcode = '28000';
  end if;

  select count(*) into v_total from public.retos_humanos r
  where r.usuario_id = v_uid and r.creado_en > now() - interval '1 hour';

  if v_total >= 10 then
    raise exception 'Demasiadas verificaciones en una hora, intenta más tarde' using errcode = '55000';
  end if;

  delete from public.retos_humanos where expira_en < now() - interval '1 day';
  delete from public.tokens_humanos where expira_en < now() - interval '1 day';

  v_forma_obj := v_formas[1 + floor(random() * 4)::int];
  v_franjas_obj := 1 + floor(random() * 4)::int;
  v_correcta := 1 + floor(random() * 4)::int;

  for v_i in 1..4 loop
    if v_i = v_correcta then
      v_forma := v_forma_obj;
      v_franjas := v_franjas_obj;
    elsif v_i % 2 = 0 then
      v_forma := v_formas[(array_position(v_formas, v_forma_obj) % 4) + 1];
      v_franjas := v_franjas_obj;
    else
      v_forma := v_forma_obj;
      v_franjas := (v_franjas_obj % 4) + 1;
    end if;

    v_piezas := v_piezas || jsonb_build_object(
      'id', v_i,
      'forma', v_forma,
      'franjas', v_franjas,
      'color', v_colores[((v_i - 1) % 4) + 1],
      'rotacion', ((v_i * 37 + v_franjas * 11) % 21) - 10
    );
  end loop;

  insert into public.retos_humanos (id, usuario_id, nonce, reto, respuesta_hash, expira_en)
  values (
    v_id, v_uid, v_nonce,
    jsonb_build_object(
      'molde', jsonb_build_object('forma', v_forma_obj, 'franjas', v_franjas_obj),
      'piezas', v_piezas
    ),
    privado.firma(v_id, v_correcta, v_nonce),
    now() + interval '3 minutes'
  );

  return jsonb_build_object(
    'id', v_id,
    'molde', jsonb_build_object('forma', v_forma_obj, 'franjas', v_franjas_obj),
    'piezas', v_piezas,
    'instruccion', 'Toca la pieza que coincide con el molde: misma forma y mismo número de franjas.',
    'expira_en', now() + interval '3 minutes',
    'min_ms', 1200
  );
end;
$$;

create or replace function public.verificar_reto_humano(
  p_reto_id uuid, p_pieza_id int, p_duracion_ms int
) returns jsonb
language plpgsql security definer set search_path = public, privado as $$
declare
  v_uid uuid := auth.uid();
  v_reto public.retos_humanos;
  v_token text;
  v_expira timestamptz;
begin
  if v_uid is null then
    raise exception 'Debes iniciar sesión' using errcode = '28000';
  end if;

  select * into v_reto from public.retos_humanos where id = p_reto_id for update;
  if not found then
    raise exception 'El reto no existe';
  end if;
  if v_reto.usuario_id <> v_uid then
    raise exception 'Este reto pertenece a otro usuario' using errcode = '42501';
  end if;
  if v_reto.resuelto_en is not null then
    raise exception 'Este reto ya fue utilizado';
  end if;
  if v_reto.expira_en < now() then
    raise exception 'El reto expiró, solicita uno nuevo';
  end if;
  if v_reto.intentos >= 3 then
    raise exception 'Demasiados intentos fallidos, solicita un reto nuevo' using errcode = '55000';
  end if;

  if coalesce(p_duracion_ms, 0) < 1200 then
    update public.retos_humanos set intentos = intentos + 1 where id = p_reto_id;
    raise exception 'La verificación fue demasiado rápida';
  end if;

  if privado.firma(v_reto.id, coalesce(p_pieza_id, -1), v_reto.nonce) <> v_reto.respuesta_hash then
    update public.retos_humanos set intentos = intentos + 1 where id = p_reto_id;
    raise exception 'La pieza elegida no coincide con el molde';
  end if;

  v_token := encode(extensions.gen_random_bytes(32), 'hex');
  v_expira := now() + interval '10 minutes';

  insert into public.tokens_humanos (usuario_id, token_hash, expira_en)
  values (v_uid, privado.hash(v_token), v_expira);

  update public.retos_humanos set resuelto_en = now() where id = p_reto_id;

  return jsonb_build_object('token', v_token, 'expira_en', v_expira);
end;
$$;

