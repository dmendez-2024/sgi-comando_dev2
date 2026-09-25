# SGI: Comando — Seguridad de Interconexiones

- Nunca guardar secretos efectivos en código, MD o `.sitcpack`.
- CORE devuelve `credentialRef`, no el secreto.
- `CredentialRefResolver` resuelve la referencia en runtime; producción puede sustituirlo por Vault/KMS.
- La lógica de negocio no puede definir `Authorization`, `Host` ni `X-Interconnection-Id`.
- HTTPS es el transporte estándar. mTLS requiere adaptador SSL de producción.
- Correlation ID obligatorio; no registrar tokens/secretos/evidencia sensible en logs.
- `instance_country_id` proviene del contexto autenticado y participa en resolución.
- El módulo no expone un endpoint proxy genérico de ejecución, para evitar SSRF y bypass de autorización.
- El ingreso `SIC_COM__SGI_COM__00001__V0001` valida `Authorization: Bearer` en el adaptador técnico como primera acción del receptor de catálogo. En UAT local se configura exclusivamente `sgi.sic-com.inbound.credential-ref`; el token efectivo se resuelve por `CredentialRefResolver` desde el entorno. Si no hay referencia o secreto disponible, el receptor responde `503`; un token ausente o no válido responde `401`.
