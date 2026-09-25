# SGI: Comando — Seguridad de Interconexiones

- El ingreso `SIC_COM__SGI_COM__00001__V0001` valida `Authorization: Bearer` en el adaptador técnico como primera acción del receptor de catálogo. En UAT local se configura exclusivamente `sgi.sic-com.inbound.credential-ref`; el token efectivo se resuelve por `CredentialRefResolver` desde el entorno. Si no hay referencia o secreto disponible, el receptor responde `503`; un token ausente o no válido responde `401`.
