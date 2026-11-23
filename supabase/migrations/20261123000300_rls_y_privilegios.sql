-- 0003 · Row Level Security y privilegios mínimos
alter table public.salas enable row level security;
alter table public.perfiles enable row level security;
alter table public.horarios enable row level security;
alter table public.reservas enable row level security;
alter table public.bloqueos_sala enable row level security;
alter table public.reserva_eventos enable row level security;
alter table public.adjuntos enable row level security;
alter table public.retos_humanos enable row level security;
alter table public.tokens_humanos enable row level security;

revoke all on public.salas, public.perfiles, public.horarios, public.reservas,
              public.bloqueos_sala, public.reserva_eventos, public.adjuntos,
              public.retos_humanos, public.tokens_humanos
  from public, anon, authenticated;

grant select on public.salas, public.horarios, public.bloqueos_sala,
                public.reservas, public.reserva_eventos, public.adjuntos,
                public.perfiles
  to authenticated;

grant update (nombre_completo, telefono, avatar_url) on public.perfiles to authenticated;

create policy salas_lectura on public.salas
  for select to authenticated using (true);

create policy horarios_lectura on public.horarios
  for select to authenticated using (true);

create policy bloqueos_lectura on public.bloqueos_sala
  for select to authenticated using (true);

create policy perfiles_lectura on public.perfiles
  for select to authenticated using (id = auth.uid() or public.es_admin());

create policy perfiles_actualizacion on public.perfiles
  for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

create policy reservas_lectura on public.reservas
  for select to authenticated using (usuario_id = auth.uid() or public.es_admin());

create policy eventos_lectura on public.reserva_eventos
  for select to authenticated
  using (exists (
    select 1 from public.reservas r
    where r.id = reserva_eventos.reserva_id
      and (r.usuario_id = auth.uid() or public.es_admin())
  ));

create policy adjuntos_lectura on public.adjuntos
  for select to authenticated
  using (subido_por = auth.uid() or public.es_admin()
         or exists (select 1 from public.reservas r
                    where r.id = adjuntos.reserva_id and r.usuario_id = auth.uid()));

-- Funciones: se revoca el EXECUTE implícito y se concede solo lo necesario
revoke all on all functions in schema public from public;
grant execute on function public.es_admin() to anon, authenticated;

grant execute on function public.crear_horarios(uuid, date[], time, time, int) to authenticated;
grant execute on function public.cambiar_estado_horario(uuid, public.estado_horario) to authenticated;
grant execute on function public.bloquear_calendario(uuid, timestamptz, timestamptz, text) to authenticated;
grant execute on function public.quitar_bloqueo(uuid) to authenticated;
grant execute on function public.cambiar_calendario_abierto(uuid, boolean) to authenticated;
grant execute on function public.crear_reto_humano() to authenticated;
grant execute on function public.verificar_reto_humano(uuid, integer, integer) to authenticated;
grant execute on function public.solicitar_reserva(uuid, text, text) to authenticated;
grant execute on function public.aprobar_reserva(uuid) to authenticated;
grant execute on function public.rechazar_reserva(uuid, text) to authenticated;
grant execute on function public.proponer_reprogramacion(uuid, date, time, time, text) to authenticated;
grant execute on function public.responder_propuesta(uuid, boolean) to authenticated;
grant execute on function public.cancelar_reserva(uuid) to authenticated;
grant execute on function public.registrar_adjunto(uuid, text) to authenticated;
grant execute on function public.cambiar_estado_usuario(uuid, public.estado_perfil) to authenticated;
grant execute on function public.cambiar_rol_usuario(uuid, public.rol_usuario) to authenticated;
