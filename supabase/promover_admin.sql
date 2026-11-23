-- Convertir en administrador a un usuario ya registrado.
-- Requiere que el usuario exista en Authentication > Users (o se haya registrado en la app).
update public.perfiles p
   set rol = 'admin', estado = 'activo'
  from auth.users u
 where u.id = p.id
   and lower(u.email) = lower('javierenrique@yahoo.com')
returning p.id, p.nombre_completo, p.rol, p.estado;

-- Comprobación posterior:
select id, nombre_completo, rol, estado, creado_en
  from public.perfiles
 order by creado_en;
