import {useEffect} from 'react';

type BadgeGroup={
  selector:string;
  trackProperty?:`--${string}`;
};

type BadgeScope={
  selector:string;
  groups:BadgeGroup[];
};

// Cada alcance corresponde a una tabla o listado con columnas. Los grupos se
// miden por separado para que, por ejemplo, Tipo y Estado no compartan ancho.
const BADGE_SCOPES:BadgeScope[]=[
  {selector:'.ter-structure-card',groups:[
    {selector:'.ter-row > .ter-status',trackProperty:'--ter-status-column-width'},
    {selector:'.ter-detail-row > .ter-status'},
    {selector:'.ter-company-list > div > .ter-status'},
  ]},
  {selector:'.com-card',groups:[
    {selector:'.com-row .com-region-chips > span',trackProperty:'--com-region-badge-width'},
    {selector:'.com-row > .com-status',trackProperty:'--com-status-column-width'},
  ]},
  {selector:'.ser-point-table',groups:[
    {selector:'tbody td:nth-child(9) .ser-state'},
  ]},
  {selector:'.ats-history-table',groups:[
    {selector:'tbody td:nth-child(6) .ats-inline-state'},
  ]},
  {selector:'.ats-mini-table',groups:[
    {selector:'tbody .ats-inline-state'},
  ]},
  {selector:'.bit-protocol-table',groups:[
    {selector:'tbody td:last-child .bit-protocol-state'},
  ]},
  {selector:'.bit-global-table',groups:[
    {selector:'tbody td:nth-child(1) .bit-global-type'},
    {selector:'tbody td:nth-child(8) .bit-global-status'},
  ]},
  {selector:'.cns-table',groups:[
    {selector:'tbody td:nth-child(1) .cns-type'},
    {selector:'tbody td:nth-child(8) .cns-status'},
  ]},
  {selector:'.nov-results-card > .nov-table-wrap > .nov-table',groups:[
    {selector:'tbody td:nth-child(1) .nov-type'},
    {selector:'tbody td:nth-child(8) .nov-status'},
    {selector:'tbody td:nth-child(6) .sup-level'},
    {selector:'tbody td:nth-child(7) .sup-level'},
    {selector:'tbody td:nth-child(8) .sup-level'},
    {selector:'tbody td:nth-child(9) .sup-level'},
    {selector:'tbody td:nth-child(10) .nov-status'},
  ]},
  {selector:'.csl-table',groups:[
    {selector:'tbody td:nth-child(1) .csl-category'},
    {selector:'tbody td:nth-child(8) .csl-status'},
    {selector:'tbody td:nth-child(9) .csl-priority'},
  ]},
  {selector:'.coord-table',groups:[
    {selector:'tbody td:nth-child(8) .coord-status'},
  ]},
  {selector:'.nexus-table:not(.nexus-history)',groups:[
    {selector:'tbody td:nth-child(3) .nexus-relation'},
    {selector:'tbody td:nth-child(6) .nexus-status'},
  ]},
  {selector:'.nexus-table.nexus-history',groups:[
    {selector:'tbody td:nth-child(4) .nexus-history-action'},
  ]},
];

function synchronizeBadges(){
  BADGE_SCOPES.forEach(definition=>{
    document.querySelectorAll<HTMLElement>(definition.selector).forEach(scope=>{
      definition.groups.forEach(group=>{
        const badges=Array.from(scope.querySelectorAll<HTMLElement>(group.selector));
        if(!badges.length)return;

        // Se elimina primero cualquier medida anterior para obtener el ancho
        // natural del contenido actual, incluidos iconos y textos dinámicos.
        badges.forEach(badge=>{
          badge.classList.add('uniform-column-badge');
          badge.style.width='max-content';
          badge.style.minWidth='0';
        });

        const width=Math.ceil(badges.reduce((largest,badge)=>{
          const measured=badge.getBoundingClientRect().width;
          return measured>largest?measured:largest;
        },0));
        if(width<=0)return;

        const value=`${width}px`;
        badges.forEach(badge=>{
          badge.style.width=value;
          badge.style.minWidth=value;
        });
        if(group.trackProperty)scope.style.setProperty(group.trackProperty,value);
      });
    });
  });
}

export default function UniformColumnBadges(){
  useEffect(()=>{
    let frame=0;
    const schedule=()=>{
      cancelAnimationFrame(frame);
      frame=requestAnimationFrame(synchronizeBadges);
    };

    const observer=new MutationObserver(schedule);
    observer.observe(document.body,{childList:true,subtree:true,characterData:true});
    window.addEventListener('resize',schedule);
    void document.fonts?.ready.then(schedule);
    schedule();

    return()=>{
      cancelAnimationFrame(frame);
      observer.disconnect();
      window.removeEventListener('resize',schedule);
    };
  },[]);

  return null;
}
