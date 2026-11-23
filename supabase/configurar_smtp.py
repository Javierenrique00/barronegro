#!/usr/bin/env python3
"""
Configura el SMTP de Brevo en el proyecto Supabase y aplica las plantillas en español.

Las credenciales se leen del entorno: no se guardan en el repositorio.

Uso:
    export SUPABASE_ACCESS_TOKEN=sbp_...      # Account -> Access Tokens
    export SMTP_KEY=xsmtpsib-...              # Brevo -> SMTP & API -> SMTP -> SMTP key
    export SMTP_FROM=barronegro.omd@gmail.com # remitente verificado en Brevo
    python3 supabase/configurar_smtp.py

Opcionales:
    SMTP_HOST      (por defecto smtp-relay.brevo.com)
    SMTP_PORT      (por defecto 587)
    SMTP_USER      (por defecto c4d179001@smtp-brevo.com)
    SMTP_NAME      (por defecto "Taller Barro Negro")
    CORREOS_POR_HORA (por defecto 50; el plan gratuito de Brevo permite 300 al día)
"""

import json
import os
import sys
import urllib.error
import urllib.request

REF = "qxsehofkuwmypuhjzult"
API = f"https://api.supabase.com/v1/projects/{REF}/config/auth"


def variable(nombre, defecto=None, obligatoria=False):
    valor = os.environ.get(nombre, defecto)
    if obligatoria and not valor:
        sys.exit(f"Falta la variable de entorno {nombre}")
    return valor


def llamar(metodo, cuerpo=None):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    peticion = urllib.request.Request(
        API,
        data=datos,
        method=metodo,
        headers={
            "Authorization": f"Bearer {variable('SUPABASE_ACCESS_TOKEN', obligatoria=True)}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(peticion) as respuesta:
            return respuesta.status, json.load(respuesta)
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode(errors="replace")


def resumen(config):
    return json.dumps(
        {
            "smtp_host": config.get("smtp_host"),
            "smtp_port": config.get("smtp_port"),
            "smtp_user": config.get("smtp_user"),
            "smtp_admin_email": config.get("smtp_admin_email"),
            "smtp_sender_name": config.get("smtp_sender_name"),
            "smtp_pass_configurada": bool(config.get("smtp_pass")),
            "rate_limit_email_sent": config.get("rate_limit_email_sent"),
            "smtp_max_frequency": config.get("smtp_max_frequency"),
            "mailer_autoconfirm": config.get("mailer_autoconfirm"),
            "uri_allow_list": config.get("uri_allow_list"),
        },
        indent=2,
        ensure_ascii=False,
    )


PLANTILLA_CONFIRMACION = (
    '<div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;color:#33140a">'
    '<h2 style="color:#8a4b2a;margin:0 0 12px">Confirma tu cuenta</h2><p>Hola,</p>'
    '<p>Gracias por registrarte en el <strong>Taller de cerámica</strong>. '
    "Pulsa el botón para activar tu cuenta:</p>"
    '<p style="margin:24px 0"><a href="{{ .ConfirmationURL }}" '
    'style="background:#8a4b2a;color:#ffffff;padding:12px 20px;border-radius:6px;'
    'text-decoration:none">Confirmar mi correo</a></p>'
    '<p style="font-size:13px;color:#556b5d">Si el botón no funciona, copia este enlace:<br>'
    "{{ .ConfirmationURL }}</p>"
    '<p style="font-size:13px;color:#556b5d">Si no te registraste, ignora este mensaje.</p></div>'
)

PLANTILLA_RECUPERACION = (
    '<div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;color:#33140a">'
    '<h2 style="color:#8a4b2a;margin:0 0 12px">Restablece tu contraseña</h2><p>Hola,</p>'
    "<p>Recibimos una solicitud para cambiar la contraseña de tu cuenta en el "
    "<strong>Taller de cerámica</strong>.</p>"
    '<p style="margin:24px 0"><a href="{{ .ConfirmationURL }}" '
    'style="background:#8a4b2a;color:#ffffff;padding:12px 20px;border-radius:6px;'
    'text-decoration:none">Elegir nueva contraseña</a></p>'
    '<p style="font-size:13px;color:#556b5d">Si el botón no funciona, copia este enlace:<br>'
    "{{ .ConfirmationURL }}</p>"
    '<p style="font-size:13px;color:#556b5d">Si no pediste el cambio, ignora este mensaje: '
    "tu contraseña actual sigue funcionando.</p></div>"
)


def main():
    smtp = {
        "smtp_host": variable("SMTP_HOST", "smtp-relay.brevo.com"),
        "smtp_port": variable("SMTP_PORT", "587"),
        "smtp_user": variable("SMTP_USER", "c4d179001@smtp-brevo.com"),
        "smtp_pass": variable("SMTP_KEY", obligatoria=True),
        "smtp_admin_email": variable("SMTP_FROM", obligatoria=True),
        "smtp_sender_name": variable("SMTP_NAME", "Taller Barro Negro"),
        "rate_limit_email_sent": int(variable("CORREOS_POR_HORA", "50")),
        "smtp_max_frequency": 60,
        "mailer_autoconfirm": False,
    }

    estado, respuesta = llamar("PATCH", smtp)
    print(f"1) SMTP: {estado}")
    if estado != 200:
        sys.exit(f"   detalle: {respuesta}")

    estado, config = llamar("GET")
    print(f"2) Estado tras el cambio ({estado}):\n{resumen(config)}")

    plantillas = {
        "mailer_subjects_confirmation": "Confirma tu cuenta en el Taller de cerámica",
        "mailer_templates_confirmation_content": PLANTILLA_CONFIRMACION,
        "mailer_subjects_recovery": "Restablece tu contraseña",
        "mailer_templates_recovery_content": PLANTILLA_RECUPERACION,
    }
    estado, respuesta = llamar("PATCH", plantillas)
    print(f"3) Plantillas: {estado}")
    if estado != 200:
        sys.exit(
            f"   detalle: {respuesta}\n"
            "   Las plantillas solo se pueden editar con SMTP propio configurado."
        )

    estado, config = llamar("GET")
    print(
        f"4) Plantillas aplicadas: asunto confirmación = "
        f"{config.get('mailer_subjects_confirmation')!r}, "
        f"recuperación = {config.get('mailer_subjects_recovery')!r}"
    )


if __name__ == "__main__":
    main()
