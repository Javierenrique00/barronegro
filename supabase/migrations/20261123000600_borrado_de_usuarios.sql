-- 0006 · Borrar un usuario no debe quedar bloqueado por su propio rastro de auditoría
alter table public.horarios drop constraint if exists horarios_creado_por_fkey;
alter table public.horarios add constraint horarios_creado_por_fkey
  foreign key (creado_por) references public.perfiles (id) on delete set null;

alter table public.bloqueos_sala drop constraint if exists bloqueos_sala_creado_por_fkey;
alter table public.bloqueos_sala add constraint bloqueos_sala_creado_por_fkey
  foreign key (creado_por) references public.perfiles (id) on delete set null;

alter table public.reserva_eventos drop constraint if exists reserva_eventos_actor_id_fkey;
alter table public.reserva_eventos add constraint reserva_eventos_actor_id_fkey
  foreign key (actor_id) references public.perfiles (id) on delete set null;

alter table public.reservas drop constraint if exists reservas_resuelto_por_fkey;
alter table public.reservas add constraint reservas_resuelto_por_fkey
  foreign key (resuelto_por) references public.perfiles (id) on delete set null;
