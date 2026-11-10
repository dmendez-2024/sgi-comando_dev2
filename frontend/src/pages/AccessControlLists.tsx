import { useEffect, useMemo, useState } from "react";
import {
  ArrowLeft,
  Download,
  Edit3,
  FileSpreadsheet,
  KeyRound,
  Plus,
  RefreshCcw,
  Search,
  ShieldAlert,
  ShieldCheck,
  Upload,
  UserPlus,
  X,
} from "lucide-react";
import { api, ApiError } from "../api";

type Point = { id: string; code: string; name: string };
type Entry = {
  id: string;
  clientId: string;
  listType: "WHITE" | "BLACK";
  objectType: string;
  identification: string;
  lastNames: string;
  firstNames: string;
  accreditation: string;
  organizationalReference?: string;
  validFrom: string;
  validUntil: string;
  status: "ACTIVE" | "INACTIVE";
  effectiveStatus: "VALID" | "EXPIRING" | "EXPIRED" | "SCHEDULED" | "INACTIVE";
  observations?: string;
  hasPhoto: boolean;
  hasQr: boolean;
  pointIds: string[];
  points: Point[];
};
type PointContext = {
  pointId: string;
  pointName: string;
  clientId: string;
  clientName: string;
};
type Draft = {
  id?: string;
  identification: string;
  lastNames: string;
  firstNames: string;
  accreditation: string;
  organizationalReference: string;
  validFrom: string;
  validUntil: string;
  status: "ACTIVE" | "INACTIVE";
  observations: string;
  pointIds: string[];
  photoBase64?: string;
  photoContentType?: string;
  photoFilename?: string;
};
const today = () => new Date().toISOString().slice(0, 10);
const empty = (pointId: string): Draft => ({
  identification: "",
  lastNames: "",
  firstNames: "",
  accreditation: "Visitante",
  organizationalReference: "",
  validFrom: today(),
  validUntil: "",
  status: "ACTIVE",
  observations: "",
  pointIds: [pointId],
});
const labels: Record<Entry["effectiveStatus"], string> = {
  VALID: "Vigente",
  EXPIRING: "Por vencer",
  EXPIRED: "Vencida",
  SCHEDULED: "Programada",
  INACTIVE: "Inactiva",
};

export default function AccessControlLists({
  point,
  listType,
  onBack,
}: {
  point: PointContext;
  listType: "WHITE" | "BLACK";
  onBack: () => void;
}) {
  const white = listType === "WHITE";
  const [rows, setRows] = useState<Entry[]>([]),
    [points, setPoints] = useState<Point[]>([]),
    [loading, setLoading] = useState(true),
    [error, setError] = useState("");
  const [q, setQ] = useState(""),
    [pointFilter, setPointFilter] = useState(""),
    [statusFilter, setStatusFilter] = useState("");
  const [drawer, setDrawer] = useState(false),
    [mode, setMode] = useState<"individual" | "file">("individual"),
    [draft, setDraft] = useState<Draft>(() => empty(point.pointId)),
    [saving, setSaving] = useState(false);
  const title = white ? "Lista Blanca" : "Lista Negra";
  const load = async () => {
    setLoading(true);
    setError("");
    try {
      const [entries, available] = await Promise.all([
        api.accessControlEntries(point.clientId, listType, {
          q,
          pointId: pointFilter,
          status: statusFilter,
        }),
        api.accessControlPoints(point.clientId),
      ]);
      setRows(entries);
      setPoints(available);
    } catch (e) {
      setError(
        e instanceof ApiError ? e.body : "No fue posible consultar la lista.",
      );
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    void load();
  }, [listType, point.clientId]);
  const openNew = () => {
    setDraft(empty(point.pointId));
    setMode("individual");
    setDrawer(true);
  };
  const edit = (row: Entry) => {
    setDraft({
      id: row.id,
      identification: row.identification,
      lastNames: row.lastNames,
      firstNames: row.firstNames,
      accreditation: row.accreditation,
      organizationalReference: row.organizationalReference ?? "",
      validFrom: row.validFrom,
      validUntil: row.validUntil,
      status: row.status,
      observations: row.observations ?? "",
      pointIds: row.pointIds,
    });
    setMode("individual");
    setDrawer(true);
  };
  const valid = useMemo(
    () =>
      draft.identification.trim() &&
      draft.lastNames.trim() &&
      draft.firstNames.trim() &&
      draft.accreditation.trim() &&
      draft.validFrom &&
      draft.validUntil &&
      draft.pointIds.length > 0,
    [draft],
  );
  const save = async () => {
    if (!valid || saving) return;
    setSaving(true);
    setError("");
    try {
      const body = {
        ...draft,
        clientId: point.clientId,
        listType,
        objectType: "PAX",
      };
      draft.id
        ? await api.updateAccessControlEntry(draft.id, body)
        : await api.createAccessControlEntry(body);
      setDrawer(false);
      await load();
    } catch (e) {
      setError(
        e instanceof ApiError ? e.body : "No fue posible guardar el registro.",
      );
    } finally {
      setSaving(false);
    }
  };
  const photo = (file?: File) => {
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      setError("La fotografía no puede superar 5 MB.");
      return;
    }
    const reader = new FileReader();
    reader.onload = () =>
      setDraft((current) => ({
        ...current,
        photoBase64: String(reader.result),
        photoContentType: file.type,
        photoFilename: file.name,
      }));
    reader.readAsDataURL(file);
  };
  const downloadQr = async (row: Entry) => {
    try {
      const blob = await api.accessControlQr(row.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `QR-${row.identification}.png`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(
        e instanceof ApiError ? e.body : "No fue posible descargar el QR.",
      );
    }
  };
  const togglePoint = (id: string) =>
    setDraft((current) => ({
      ...current,
      pointIds: current.pointIds.includes(id)
        ? current.pointIds.filter((value) => value !== id)
        : [...current.pointIds, id],
    }));
  return (
    <div className="acl-page">
      <div className="acl-breadcrumb">
        <button onClick={onBack}>
          <ArrowLeft size={15} />
          Control de Accesos
        </button>
        <span>/</span>
        <b>{title}</b>
      </div>
      <header className="acl-header">
        <span className={white ? "white" : "black"}>
          {white ? <ShieldCheck /> : <ShieldAlert />}
        </span>
        <div>
          <h2>{title}</h2>
          <p>
            {white
              ? "Personas autorizadas para el acceso."
              : "Personas con acceso restringido o sujetas a revisión."}
          </p>
        </div>
        <div className="acl-header-actions">
          <button
            className="ghost"
            onClick={() => {
              openNew();
              setMode("file");
            }}
          >
            <Upload size={16} />
            Importar
          </button>
          <button className="primary" onClick={openNew}>
            <Plus size={16} />
            {white ? "Nueva autorización" : "Nueva restricción"}
          </button>
        </div>
      </header>
      {!white && (
        <div className="acl-error">
          Los puntos seleccionados definen exactamente dónde la persona tiene
          prohibido el ingreso. Ante coincidencia, la Lista Negra prevalece
          sobre la Lista Blanca.
        </div>
      )}
      {error && <div className="acl-error">{error}</div>}
      <section className="acl-filters">
        <label>
          <Search size={17} />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Buscar por identificación o nombre…"
          />
        </label>
        <select
          value={pointFilter}
          onChange={(e) => setPointFilter(e.target.value)}
        >
          <option value="">Todos los puntos</option>
          {points.map((item) => (
            <option key={item.id} value={item.id}>
              {item.name}
            </option>
          ))}
        </select>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
        >
          <option value="">Todos los estados</option>
          <option value="ACTIVE">Activos</option>
          <option value="INACTIVE">Inactivos</option>
        </select>
        <button onClick={() => void load()}>
          <RefreshCcw size={16} />
          Buscar
        </button>
      </section>
      <section className="acl-table-card">
        <h3>
          {white ? "Autorizaciones" : "Restricciones"} ({rows.length})
        </h3>
        <div className="acl-table-wrap">
          <table>
            <thead>
              <tr>
                <th>Identificación</th>
                <th>Apellidos y nombres</th>
                <th>Acreditación</th>
                <th>Puntos</th>
                <th>Vigencia</th>
                <th>Estado</th>
                {white && <th>QR</th>}
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {!loading &&
                rows.map((row) => (
                  <tr key={row.id}>
                    <td>
                      <b>{row.identification}</b>
                    </td>
                    <td>
                      {row.lastNames} {row.firstNames}
                    </td>
                    <td>{row.accreditation}</td>
                    <td>
                      <div className="acl-tags">
                        {row.points.map((item) => (
                          <span key={item.id}>{item.name}</span>
                        ))}
                      </div>
                    </td>
                    <td>
                      {row.validFrom} — {row.validUntil}
                    </td>
                    <td>
                      <span
                        className={`acl-state ${row.effectiveStatus.toLowerCase()}`}
                      >
                        {labels[row.effectiveStatus]}
                      </span>
                    </td>
                    {white && (
                      <td>
                        {row.hasQr ? (
                          <button
                            className="icon"
                            title="Descargar QR"
                            onClick={() => void downloadQr(row)}
                          >
                            <Download size={16} />
                          </button>
                        ) : (
                          "—"
                        )}
                      </td>
                    )}
                    <td>
                      <button
                        className="icon"
                        title="Editar"
                        onClick={() => edit(row)}
                      >
                        <Edit3 size={16} />
                      </button>
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
          {loading && <div className="acl-empty">Consultando registros…</div>}
          {!loading && !rows.length && (
            <div className="acl-empty">
              No hay registros con los filtros aplicados.
            </div>
          )}
        </div>
      </section>
      {drawer && (
        <>
          <div
            className="acl-overlay"
            onClick={() => !saving && setDrawer(false)}
          />
          <aside className="acl-drawer">
            <header>
              <div>
                <h2>
                  {draft.id
                    ? "Editar"
                    : white
                      ? "Nueva autorización"
                      : "Nueva restricción"}
                </h2>
                <p>
                  {white
                    ? "Registra una persona en Lista Blanca."
                    : "Registra una persona en Lista Negra."}
                </p>
              </div>
              <button className="icon" onClick={() => setDrawer(false)}>
                <X />
              </button>
            </header>
            <div className="acl-tabs">
              <button
                className={mode === "individual" ? "active" : ""}
                onClick={() => setMode("individual")}
              >
                <UserPlus size={16} />
                Registro individual
              </button>
              <button
                className={mode === "file" ? "active" : ""}
                onClick={() => setMode("file")}
              >
                <FileSpreadsheet size={16} />
                Carga por archivo
              </button>
            </div>
            {mode === "file" ? (
              <div className="acl-import">
                <FileSpreadsheet size={42} />
                <h3>Carga masiva</h3>
                <p>
                  La plantilla deberá incluir identificación, apellidos,
                  nombres, acreditación, puntos, vigencia, estado y
                  observaciones. La importación se habilitará cuando se apruebe
                  el formato oficial.
                </p>
                <button disabled>
                  <Upload size={16} />
                  Seleccionar archivo
                </button>
              </div>
            ) : (
              <div className="acl-form">
                <div className="two">
                  <label>
                    Identificación *
                    <input
                      value={draft.identification}
                      onChange={(e) =>
                        setDraft({ ...draft, identification: e.target.value })
                      }
                    />
                  </label>
                  <label>
                    Acreditación *
                    <input
                      value={draft.accreditation}
                      onChange={(e) =>
                        setDraft({ ...draft, accreditation: e.target.value })
                      }
                    />
                  </label>
                </div>
                <label>
                  Organización / área / cargo (opcional)
                  <input
                    placeholder="Ej. Finanzas, Analista de Seguridad o Proveedor S.A."
                    value={draft.organizationalReference}
                    onChange={(e) =>
                      setDraft({
                        ...draft,
                        organizationalReference: e.target.value,
                      })
                    }
                  />
                </label>
                <div className="two">
                  <label>
                    Apellidos *
                    <input
                      value={draft.lastNames}
                      onChange={(e) =>
                        setDraft({ ...draft, lastNames: e.target.value })
                      }
                    />
                  </label>
                  <label>
                    Nombres *
                    <input
                      value={draft.firstNames}
                      onChange={(e) =>
                        setDraft({ ...draft, firstNames: e.target.value })
                      }
                    />
                  </label>
                </div>
                <fieldset>
                  <legend>Puntos permitidos / restringidos *</legend>
                  {points.map((item) => (
                    <label key={item.id}>
                      <input
                        type="checkbox"
                        checked={draft.pointIds.includes(item.id)}
                        onChange={() => togglePoint(item.id)}
                      />
                      {item.name}
                    </label>
                  ))}
                </fieldset>
                <div className="two">
                  <label>
                    Inicio de vigencia *
                    <input
                      type="date"
                      value={draft.validFrom}
                      onChange={(e) =>
                        setDraft({ ...draft, validFrom: e.target.value })
                      }
                    />
                  </label>
                  <label>
                    Fin de vigencia *
                    <input
                      type="date"
                      min={draft.validFrom}
                      value={draft.validUntil}
                      onChange={(e) =>
                        setDraft({ ...draft, validUntil: e.target.value })
                      }
                    />
                  </label>
                </div>
                <div className="two">
                  <label>
                    Estado *
                    <select
                      value={draft.status}
                      onChange={(e) =>
                        setDraft({
                          ...draft,
                          status: e.target.value as Draft["status"],
                        })
                      }
                    >
                      <option value="ACTIVE">Activo</option>
                      <option value="INACTIVE">Inactivo</option>
                    </select>
                  </label>
                  <label>
                    Fotografía (opcional)
                    <input
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      onChange={(e) => photo(e.target.files?.[0])}
                    />
                  </label>
                </div>
                <label>
                  Observaciones
                  <textarea
                    maxLength={1000}
                    value={draft.observations}
                    onChange={(e) =>
                      setDraft({ ...draft, observations: e.target.value })
                    }
                  />
                  <small>{draft.observations.length}/1000</small>
                </label>
                {white && (
                  <div className="acl-qr-note">
                    <KeyRound size={19} />
                    <span>
                      Al crear el registro se generará un QR individual,
                      revocable y sin datos personales visibles.
                    </span>
                  </div>
                )}
              </div>
            )}
            <footer>
              <button className="ghost" onClick={() => setDrawer(false)}>
                Cancelar
              </button>
              {mode === "individual" && (
                <button
                  className="primary"
                  disabled={!valid || saving}
                  onClick={() => void save()}
                >
                  {saving ? "Guardando…" : "Guardar registro"}
                </button>
              )}
            </footer>
          </aside>
        </>
      )}
    </div>
  );
}
