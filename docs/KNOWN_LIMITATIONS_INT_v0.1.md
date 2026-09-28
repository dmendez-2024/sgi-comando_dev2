> **HISTÓRICO / SUPERSEDED 2026-09-27:** Documento histórico de INT v0.1. Los IDs v3 aquí citados son aliases; consultar `docs/KNOWN_LIMITATIONS.md`, `docs/INTERCONNECTIONS.md` y CURRENT para el estado vigente.

# Known Limitations — INT v0.1

- El módulo genérico está implementado, pero los adapters de negocio aún deben migrarse gradualmente desde providers LOCAL/ad-hoc hacia `GenericInterconnectionExecutor` usando los IDs canónicos.
- CORE runtime resolver debe implementar/verificar `SGI_COM__CORE__00002__V0001` antes de tráfico real.
- IDENT está NOT_STARTED en el snapshot maestro.
- SIC_COM, SIC_RRHH, SMC, STC, VISINT, SGI_CLT y CM_CON son legacy; sus cambios quedan MANUAL_PENDING.
- SIC_RRMM y SGI_OPR requieren su propio módulo genérico en sus respectivos programas.
- Los contratos inbound descritos en SITC no se consideran end-to-end terminados hasta que ambas puntas estén implementadas y probadas.
- Alarmas electrónicas de Consola no tienen todavía Program ID fuente/gateway en el snapshot maestro; no se inventó una interconexión.
- mTLS requiere un adapter de SSL/keystore de producción; `CredentialRefResolver` actual es la implementación UAT/bootstrap y puede sustituirse por Vault/KMS.
