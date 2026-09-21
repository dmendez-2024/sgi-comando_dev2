# SGI: Comando — Seguridad de Interconexiones

- Nunca guardar secretos efectivos en código, MD o `.sitcpack`.
- CORE devuelve `credentialRef`, no el secreto.
- `CredentialRefResolver` resuelve la referencia en runtime; producción puede sustituirlo por Vault/KMS.
- La lógica de negocio no puede definir `Authorization`, `Host` ni `X-Interconnection-Id`.
- HTTPS es el transporte estándar. mTLS requiere adaptador SSL de producción.
- Correlation ID obligatorio; no registrar tokens/secretos/evidencia sensible en logs.
- `instance_country_id` proviene del contexto autenticado y participa en resolución.
- El módulo no expone un endpoint proxy genérico de ejecución, para evitar SSRF y bypass de autorización.
