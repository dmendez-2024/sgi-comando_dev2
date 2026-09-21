import { useEffect, useState } from 'react';
import { api } from '../api';

export default function Operations() {
  const [services, setServices] = useState<any[]>([]);
  const [points, setPoints] = useState<any[]>([]);
  const [posts, setPosts] = useState<any[]>([]);
  const [selected, setSelected] = useState<string>('');
  const [serviceErr, setServiceErr] = useState('');
  const [postErr, setPostErr] = useState('');

  useEffect(() => {
    void (async () => {
      try {
        const s = await api.services();
        setServices(s);
        setServiceErr('');
        if (s[0]) {
          const p = await api.points(s[0].id);
          setPoints(p);
          if (p[0]) {
            setSelected(p[0].id);
            try {
              setPosts(await api.posts(p[0].id));
              setPostErr('');
            } catch (error) {
              setPostErr(String(error));
            }
          }
        }
      } catch (error) {
        setServiceErr(String(error));
      }
    })();
  }, []);

  async function pick(id: string) {
    try {
      setSelected(id);
      setPosts(await api.posts(id));
      setPostErr('');
    } catch (error) {
      setPosts([]);
      setPostErr(String(error));
    }
  }

  return (
    <div className="grid2">
      <div className="panel">
        <h2>Servicios y Puntos</h2>
        {serviceErr && <div className="error">{serviceErr}</div>}
        {services.map((s) => (
          <div key={s.id}>
            <div className="service"><strong>{s.clientName}</strong><span>{s.name}</span></div>
            {points.map((p) => (
              <button key={p.id} onClick={() => void pick(p.id)} className={`pointrow ${selected === p.id ? 'selected' : ''}`}>
                <span>{p.name}</span><small>{p.city}, {p.province}</small>
              </button>
            ))}
          </div>
        ))}
      </div>
      <div className="panel">
        <h2>Puestos</h2>
        {postErr && <div className="error">{postErr}</div>}
        <table><thead><tr><th>Código</th><th>Puesto</th><th>Formato</th><th>FHE</th><th>TIER</th></tr></thead>
          <tbody>{posts.map((p) => <tr key={p.id}><td className="mono">{p.code}</td><td>{p.name}</td><td>{p.format}</td><td>{p.fhe}</td><td>{p.tier}</td></tr>)}</tbody>
        </table>
      </div>
    </div>
  );
}
