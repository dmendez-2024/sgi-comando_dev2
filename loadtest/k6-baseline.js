import http from 'k6/http';
import { check, sleep } from 'k6';
import encoding from 'k6/encoding';
const VUS=Number(__ENV.TARGET_VUS||200);
const DURATION=__ENV.DURATION||'2m';
const COMPANY=__ENV.COMPANY_ID||'20000000-0000-0000-0000-000000000001';
const WEEK=__ENV.WEEK_START||'2026-09-07';
export const options={vus:VUS,duration:DURATION,thresholds:{http_req_failed:['rate<0.01'],http_req_duration:['p(95)<400','p(99)<900']}};
const auth='Basic '+encoding.b64encode('supervisor:CajamarcaUAT!2026');
export default function(){
 const p={headers:{Authorization:auth}};
 check(http.get('http://localhost:8080/api/context',p),{'context 200':r=>r.status===200});
 check(http.get('http://localhost:8080/api/companies?page=0&size=25',p),{'companies 200':r=>r.status===200});
 check(http.get(`http://localhost:8080/api/assignments/personnel?companyId=${COMPANY}&weekStart=${WEEK}&page=0&size=50`,p),{'assignment personnel 200':r=>r.status===200});
 check(http.get(`http://localhost:8080/api/assignments/week?companyId=${COMPANY}&weekStart=${WEEK}`,p),{'assignment week 200':r=>r.status===200});
 sleep(Math.random()*2);
}
