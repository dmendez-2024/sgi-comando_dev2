// Login de SGI: Comando con IDENT (OIDC Authorization Code + PKCE), igual que SIC_RRMM y CORE.
// La direccion de IDENT NO esta fija: el backend la resuelve en CORE (SGI_COM_IDENT_0001_v001) y la entrega en
// /api/v1/auth/ident-config. Si el backend no tiene IDENT encendido, SGI sigue con el selector "Usuario UAT".
//  1. Sin token valido -> la persona va a IDENT /oauth2/authorize. Con sesion de IDENT abierta vuelve al instante (SSO).
//  2. <base>/oauth/callback -> cambia el code por el token en IDENT /oauth2/token.
//  3. Las llamadas a la API llevan Authorization: Bearer <token>; el Rol sale del claim "groups" (Cargo en IDENT).
// El token vive SOLO en memoria: nunca se reutiliza el de una persona anterior en el mismo navegador.
const API = import.meta.env.VITE_API_URL || '/dev.comando';
const BASE = import.meta.env.BASE_URL.replace(/\/+$/, '');   // '/dev.comando' (local y desarrollo) o '/comando' (produccion)
const REDIRECT_URI = `${window.location.origin}${BASE}/oauth/callback`;
let IDENT_URL = '';
let CLIENT_ID = '';
let accessToken: string | null = null;

export type IdentClaims = { sub: string; aud: string | string[]; groups?: string[]; email?: string; exp: number };

export const identEnabled = () => IDENT_URL !== '';
export const identToken = () => accessToken;

function b64url(bytes: Uint8Array) { let s = ''; bytes.forEach(b => (s += String.fromCharCode(b))); return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''); }
function random(n = 32) { const b = new Uint8Array(n); crypto.getRandomValues(b); return b64url(b); }
async function sha256(v: string) { return b64url(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(v)))); }

export function identClaims(): IdentClaims | null {
  if (!accessToken) return null;
  try {
    const c = JSON.parse(atob(accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) as IdentClaims;
    return c.exp * 1000 > Date.now() + 15_000 ? c : null;   // vencido (o por vencer): pedir uno nuevo
  } catch { return null; }
}

/** Roles de SGI: Comando que entrega IDENT segun el Cargo de la persona. */
export const identRoles = () => identClaims()?.groups ?? [];
/** Correo de la persona (IDENT lo envia en el token). */
export const identEmail = () => identClaims()?.email ?? '';

export async function startLogin() {
  const verifier = random(48), state = random(20);
  sessionStorage.setItem('sgi_pkce_verifier', verifier);
  sessionStorage.setItem('sgi_oauth_state', state);
  sessionStorage.setItem('sgi_return_to', window.location.pathname + window.location.search);
  const q = new URLSearchParams({ client_id: CLIENT_ID, redirect_uri: REDIRECT_URI, response_type: 'code', state,
    code_challenge: await sha256(verifier), code_challenge_method: 'S256' });
  window.location.assign(`${IDENT_URL}/oauth2/authorize?${q}`);
}

async function finishLogin() {
  const q = new URLSearchParams(window.location.search);
  if (q.get('state') !== sessionStorage.getItem('sgi_oauth_state')) throw new Error('Respuesta de IDENT inválida (state). Vuelve a ingresar.');
  const body = new URLSearchParams({ grant_type: 'authorization_code', code: q.get('code') || '', client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI, code_verifier: sessionStorage.getItem('sgi_pkce_verifier') || '' });
  const r = await fetch(`${IDENT_URL}/oauth2/token`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body });
  const x = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(x.code === 'ACCESS_DENIED' ? 'No tienes acceso a SGI: Comando. Consulta con tu administrador.' : (x.detail || x.code || 'IDENT no entregó el token.'));
  setToken(x.access_token);
  sessionStorage.removeItem('sgi_pkce_verifier'); sessionStorage.removeItem('sgi_oauth_state');
  window.history.replaceState({}, '', sessionStorage.getItem('sgi_return_to') || `${BASE}/`);
}

// Renovacion sin recargar: el token de IDENT para la web dura 10 minutos y no trae refresh_token. Un minuto antes de
// que venza se repite el login en un iframe oculto: con la sesion de IDENT abierta vuelve al callback con un code
// (mismo dominio, se lee su URL) y se cambia por un token nuevo. Sin sesion, IDENT muestra /ingresar (no respeta
// prompt=none): se abandona y la siguiente llamada con 401 hace el login normal.
const RENEW_BEFORE_MS = 60_000, RENEW_TIMEOUT_MS = 15_000;
let renewTimer: number | undefined;
let renewing: Promise<boolean> | null = null;

function setToken(token: string) {
  accessToken = token;
  window.clearTimeout(renewTimer);
  const exp = identClaims()?.exp;
  if (exp) renewTimer = window.setTimeout(() => void renewIdentToken(), Math.max(exp * 1000 - Date.now() - RENEW_BEFORE_MS, 0));
}

/** Pide un token nuevo a IDENT sin salir de la pagina. true = hay token nuevo; false = hay que hacer el login normal. */
export function renewIdentToken(): Promise<boolean> {
  if (!identEnabled()) return Promise.resolve(false);
  renewing ??= renewInFrame().catch(() => false).finally(() => { renewing = null; });
  return renewing;
}

async function renewInFrame(): Promise<boolean> {
  const verifier = random(48), state = random(20);
  const q = new URLSearchParams({ client_id: CLIENT_ID, redirect_uri: REDIRECT_URI, response_type: 'code', state,
    code_challenge: await sha256(verifier), code_challenge_method: 'S256', prompt: 'none' });
  const frame = document.createElement('iframe');
  frame.style.display = 'none'; frame.src = `${IDENT_URL}/oauth2/authorize?${q}`;
  document.body.appendChild(frame);
  let code: string | null = null;
  try {
    for (const t0 = Date.now(); Date.now() - t0 < RENEW_TIMEOUT_MS;) {
      await new Promise(r => setTimeout(r, 100));
      let url: URL;
      try { url = new URL(frame.contentWindow!.location.href); } catch { continue; }   // aun en otro origen o cargando
      if (url.pathname === `${BASE}/oauth/callback`) { if (url.searchParams.get('state') === state) code = url.searchParams.get('code'); break; }
      if (/\/(ingresar|login)$/.test(url.pathname)) break;                           // sin sesion de IDENT
    }
  } finally { frame.remove(); }
  if (!code) return false;
  const body = new URLSearchParams({ grant_type: 'authorization_code', code, client_id: CLIENT_ID, redirect_uri: REDIRECT_URI, code_verifier: verifier });
  const r = await fetch(`${IDENT_URL}/oauth2/token`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body });
  const x = await r.json().catch(() => ({}));
  if (!r.ok || !x.access_token) return false;
  setToken(x.access_token);
  return true;
}

/** Cuantas Plataformas tiene la persona en IDENT (con 2 o mas se muestra "Plataformas"). null si no se pudo saber. */
export async function identPlatforms(): Promise<{ count: number; portalUrl: string } | null> {
  if (!identEnabled() || !accessToken) return null;
  try {
    const r = await fetch(`${IDENT_URL}/api/v1/me/platforms`, { headers: { Authorization: `Bearer ${accessToken}` } });
    if (!r.ok) return null;
    const x = await r.json();
    return { count: x.count || 0, portalUrl: x.portal_url };
  } catch { return null; }
}

/** Volver al portal de Plataformas de IDENT. */
export function identPortal() { accessToken = null; window.location.assign(`${IDENT_URL}/sistemas`); }

/** Cerrar sesion: la cierra IDENT (pagina /salir) para todos los Sistemas. */
export function identSignOut() { accessToken = null; window.location.assign(`${IDENT_URL}/salir`); }

/** Antes de mostrar SGI: lee la configuracion y termina o inicia el login. true = se puede mostrar SGI. */
export async function ensureIdentLogin(): Promise<boolean> {
  // Dentro del iframe de renovacion: el callback lo procesa la pagina principal (no gastar el code ni mostrar SGI).
  if (window.self !== window.top && window.location.pathname === `${BASE}/oauth/callback`) return false;
  const r = await fetch(`${API}/api/v1/auth/ident-config`);
  if (!r.ok) throw new Error(`SGI: Comando no pudo obtener la configuración de ingreso (HTTP ${r.status}).`);
  const c = await r.json() as { enabled: boolean; identUrl?: string; clientId?: string };
  if (!c.enabled || !c.identUrl || !c.clientId) return true;
  IDENT_URL = c.identUrl.replace(/\/+$/, ''); CLIENT_ID = c.clientId;
  if (window.location.pathname === `${BASE}/oauth/callback`) await finishLogin();
  if (!identClaims()) { await startLogin(); return false; }
  return true;
}
