/* ---- Temas, panel OBD-II y navegación guiada (2.26): demostraciones animadas ---- */
(function(){
var $=function(s){return document.querySelector(s)}, $$=function(s){return Array.prototype.slice.call(document.querySelectorAll(s))};
var reduce=window.matchMedia&&matchMedia('(prefers-reduced-motion: reduce)').matches;
function L(){var l=window.I18N?I18N.lang():'es';return l==='ms'?'es':l==='fr'?'en':l}
function T(s){return window.I18N?I18N.T(s):s}
/* escala un lienzo de 1024×600 al ancho de su pantalla */
function fitter(screen,stage){function f(){var s=screen.clientWidth/1024;stage.style.transform='scale('+s+')'}addEventListener('resize',f);f();return f}
/* corre solo mientras se ve (ahorra batería en el celular) */
function whenVisible(el,on,off){if(!('IntersectionObserver' in window)){on();return}
 new IntersectionObserver(function(es){es.forEach(function(e){e.isIntersecting?on():off()})},{threshold:.2}).observe(el)}

/* ===== Temas: cambia solo de tema con una transición circular desde el engranaje ===== */
(function(){
 var A=$('#tsA'),B=$('#tsB'),scr=$('#tsScreen');if(!A)return;
 var SEQ=[['clasico','oscuro'],['clasico','claro'],['aurora','oscuro'],['aurora','claro'],['cobalto','oscuro'],['cobalto','claro'],['faro','oscuro'],['faro','claro'],['carbono','oscuro'],['carbono','claro'],['cards','oscuro'],['cards','claro']];
 var NAMES={clasico:'Clásico',aurora:'Aurora',cobalto:'Cobalto',faro:'Faro',carbono:'Carbono',cards:'Premium Cards'},MODES={oscuro:'Oscuro',claro:'Claro'};
 var cur=0,timer=null,busy=false,running=false,hold=0,DWELL=4200,prog=$('#tsProg');
 function src(i){return 'img/tema-'+SEQ[i][0]+'-'+SEQ[i][1]+'-'+L()+'.webp'}
 function ui(i){var th=SEQ[i][0],md=SEQ[i][1];
  $$('#tsThemes button').forEach(function(b){b.classList.toggle('on',b.dataset.th===th)});
  $$('#tsModes button').forEach(function(b){b.classList.toggle('on',b.dataset.md===md)});
  $$('.tcard').forEach(function(c){c.classList.toggle('on',c.dataset.th===th)});
  A.alt=B.alt='StradaOS · '+NAMES[th]+' · '+T(MODES[md])}
 function bar(){if(!prog)return;prog.style.transition='none';prog.style.width='0';void prog.offsetWidth;
  if(running&&!reduce){prog.style.transition='width '+DWELL+'ms linear';prog.style.width='100%'}}
 function show(i){if(busy||i===cur&&A.src.indexOf(src(i))>=0)return;busy=true;var s=src(i),im=new Image();
  im.onload=im.onerror=function(){B.src=s;B.classList.remove('go');void B.offsetWidth;
   if(reduce){A.src=s;busy=false;cur=i;ui(i);return}
   B.classList.add('go');A.classList.add('out');ui(i);
   setTimeout(function(){A.src=s;A.classList.remove('out');B.classList.remove('go');cur=i;busy=false;
    var nx=new Image();nx.src=src((i+1)%SEQ.length)},1100)};
  im.src=s}
 function loop(){clearTimeout(timer);if(!running)return;bar();timer=setTimeout(function(){if(Date.now()<hold){loop();return}show((cur+1)%SEQ.length);setTimeout(loop,1150)},DWELL)}
 function pick(th,md){hold=Date.now()+9000;for(var i=0;i<SEQ.length;i++)if(SEQ[i][0]===th&&SEQ[i][1]===md){show(i);break}loop()}
 $$('#tsThemes button').forEach(function(b){b.addEventListener('click',function(){pick(b.dataset.th,SEQ[cur][1])})});
 $$('#tsModes button').forEach(function(b){b.addEventListener('click',function(){pick(SEQ[cur][0],b.dataset.md)})});
 $$('.tcard').forEach(function(c){c.addEventListener('click',function(){pick(c.dataset.th,SEQ[cur][1])})});
 document.addEventListener('langchange',function(){A.src=B.src=src(cur);ui(cur)});
 A.src=B.src=src(0);ui(0);
 whenVisible(scr,function(){if(!running){running=true;loop()}},function(){running=false;clearTimeout(timer);bar()});
})();

/* ===== Panel OBD-II: sale encima del tema, los medidores y el gráfico se mueven ===== */
(function(){
 var od=$('#od'),scr=$('#odScreen');if(!od)return;fitter(scr,od);
 var bg=$('#odBg'),bl=$('#odBlur');
 function setBg(){var s='img/tema-aurora-oscuro-'+L()+'.webp';bg.src=bl.src=s}
 document.addEventListener('langchange',setBg);setBg();
 var G={};$$('.od-g').forEach(function(g){G[g.dataset.k]={arc:g.querySelector('.ar'),v:g.querySelector('.v')}});
 var GREEN='#3DDC84',AMBER='#F5B800',RED='#E3262F';
 function num(v,d){return v.toFixed(d).replace('.',',').replace(/\B(?=(\d{3})+(?!\d))/g,'.')}
 function gauge(k,v,min,max,txt,col){var g=G[k],f=Math.max(0,Math.min(1,(v-min)/(max-min)));g.arc.style.strokeDashoffset=(236*(1-f)).toFixed(1);g.arc.style.stroke=col;g.v.textContent=txt}
 var hist=[],N=70,t=0,cool=84,rpm=900,spd=0,tgt=40,timer=null,phase=0,open=false,running=false;
 for(var i=0;i<N;i++)hist.push(84+Math.sin(i/6)*1.5);
 function chart(){var max=112,min=70,w=700,h=120,d='';hist.forEach(function(v,i){var x=i*w/(N-1),y=h-(v-min)/(max-min)*h;d+=(i?'L':'M')+x.toFixed(1)+' '+y.toFixed(1)});
  $('#odLine').setAttribute('d',d);$('#odFill').setAttribute('d',d+'L'+w+' '+h+'L0 '+h+'Z')}
 function step(){t++;
  // refrigerante: sube hasta pasar el aviso (100 °C) y vuelve a lo normal
  var c=t%90;var goal=c<30?88:c<52?103:c<70?95:89;cool+=(goal-cool)*.08+(Math.random()-.5)*.25;
  if(Math.random()<.12)tgt=Math.max(0,Math.min(78,tgt+(Math.random()*44-20)));spd+=(tgt-spd)*.15;
  var r=spd<3?850:900+spd*38+Math.sin(t/3)*180;rpm+=(r-rpm)*.3;
  var volt=14.05+Math.sin(t/7)*.08;
  gauge('cool',cool,40,130,num(cool,0),cool>=106?RED:cool>=100?AMBER:GREEN);
  gauge('volt',volt,10,16,num(volt,1),volt<11.8?RED:volt<12.6||volt>15.2?AMBER:GREEN);
  gauge('rpm',rpm,0,7000,num(rpm,0),rpm>=6000?RED:rpm>=5200?AMBER:GREEN);
  $('#odSpd').textContent=num(spd,0)+' km/h';$('#odLoad').textContent=num(Math.max(12,Math.min(92,18+spd*.6+Math.sin(t/4)*6)),0)+' %';
  $('#odCons').textContent=num(spd<3?0:4.2+Math.sin(t/5)*1.1+(cool>98?.6:0),1)+' L/100 km';
  $('#odCoolBig').textContent=num(cool,0)+' °C';
  hist.push(cool);hist.shift();chart();
  var al=$('#odAlert');if(cool>=100){$('#odAlertT').textContent=T('Refrigerante alto: {} °C').replace('{}',num(cool,0));al.classList.add('on')}else al.classList.remove('on')}
 function cycle(){// abre el panel, lo deja ~13 s y lo cierra: se ve cómo sale encima del tema
  if(!running)return;phase++;
  if(!open){od.classList.add('open');open=true;timer=setTimeout(cycle,13500)}
  else{od.classList.remove('open');open=false;timer=setTimeout(cycle,2600)}}
 var iv=null;
 whenVisible(scr,function(){if(running)return;running=true;if(reduce){od.classList.add('open');step();return}
   iv=setInterval(step,300);timer=setTimeout(cycle,900)},
  function(){running=false;clearInterval(iv);clearTimeout(timer);od.classList.remove('open');open=false});
 step();chart();
})();

/* ===== Navegación guiada: el mapa se mueve, la ruta se va recorriendo y cambia la instrucción ===== */
(function(){
 var nv=$('#nv'),scr=$('#nvScreen');if(!nv)return;fitter(scr,nv);
 var NS='http://www.w3.org/2000/svg',W=$('#nvWorld');
 function el(n,a,p){var e=document.createElementNS(NS,n);for(var k in a)e.setAttribute(k,a[k]);(p||W).appendChild(e);return e}
 // mundo de ejemplo: cuadras de 170 px, una avenida diagonal, un parque y un río (1 px ≈ 1,6 m)
 var S=170,X0=-400,Y0=-600,X1=2600,Y1=2600;
 el('rect',{x:X0,y:Y0,width:X1-X0,height:Y1-Y0,fill:'#1A1D23'});
 var seed=7;function rnd(){seed=(seed*16807)%2147483647;return seed/2147483647}
 var SH=['#21252C','#23272F','#1F232A','#25292F'];
 for(var x=X0;x<X1;x+=S)for(var y=Y0;y<Y1;y+=S){el('rect',{x:x+9,y:y+9,width:S-18,height:S-18,rx:6,fill:SH[Math.floor(rnd()*4)]});
  // edificios dentro de cada cuadra
  for(var bx=0;bx<3;bx++)for(var by=0;by<3;by++)if(rnd()<.7)el('rect',{x:x+17+bx*46,y:y+17+by*46,width:30+rnd()*8,height:30+rnd()*8,rx:3,fill:'#2B3038'})}
 el('rect',{x:1030,y:180,width:320,height:320,rx:10,'class':'park'});
 el('path',{d:'M-400 1500 C 200 1380, 500 1700, 1100 1560 S 2000 1500, 2600 1700 L2600 1800 C 2000 1610, 1600 1680, 1100 1680 S 300 1520, -400 1620Z','class':'water'});
 for(var x=X0;x<X1;x+=S)el('path',{d:'M'+x+' '+Y0+'V'+Y1,'class':'st'});
 for(var x=X0+S/2;x<X1;x+=S*2)el('path',{d:'M'+x+' '+Y0+'V'+Y1,'class':'st2'});
 for(var y=Y0;y<Y1;y+=S)el('path',{d:'M'+X0+' '+y+'H'+X1,'class':'st'});
 el('path',{d:'M-300 2300 L 2300 -300','class':'av'});el('path',{d:'M-300 2300 L 2300 -300','class':'avc'});
 // nombres de calles (Carrera = vertical, Calle = horizontal)
 var CR={'0':'Carrera 50','170':'Carrera 48','340':'Carrera 46','510':'Carrera 45','680':'Carrera 43','850':'Carrera 42','1020':'Carrera 41','1190':'Carrera 40','1360':'Carrera 39','1530':'Carrera 38'};
 var CL={'1360':'Calle 40','1190':'Calle 42','1020':'Calle 44','850':'Calle 45','680':'Calle 47','510':'Calle 49','340':'Calle 50','170':'Calle 52','0':'Calle 54'};
 for(var k in CR){var lx=+k;for(var yy=-500;yy<2500;yy+=680){var t=el('text',{x:lx-6,y:yy,'class':'lb',transform:'rotate(-90 '+(lx-6)+' '+yy+')'});t.textContent=CR[k]}}
 for(var k in CL){var ly=+k;for(var xx=-300;xx<2500;xx+=680){var t=el('text',{x:xx,y:ly-12,'class':'lb'});t.textContent=CL[k]}}
 var pk=el('text',{x:1110,y:345,'class':'lbp'});pk.textContent='Parque';
 // ruta: puntos sobre las calles; cada tramo con su calle
 var P=[[340,1530],[340,1020],[850,1020],[850,510],[1360,510],[1360,170],[1530,170]];
 var NAMES=['Carrera 46','Calle 44','Carrera 42','Calle 49','Carrera 39','Calle 52'];
 var DEST='Parque Explora';
 var d='M'+P.map(function(p){return p.join(' ')}).join('L');
 el('path',{d:d,'class':'rto'});var rt=el('path',{d:d,'class':'rt'});el('path',{d:d,'class':'rtc'});
 var done=el('path',{d:'M'+P[0].join(' '),'class':'done'});
 el('circle',{cx:P[P.length-1][0],cy:P[P.length-1][1],r:13,'class':'dest'});
 var seg=[],tot=0;for(var i=1;i<P.length;i++){var l=Math.abs(P[i][0]-P[i-1][0])+Math.abs(P[i][1]-P[i-1][1]);seg.push(l);tot+=l}
 var M=1.6,car=$('#nvCar'),CX=512,CY=380,pos=0,spd=0,head=0,timer=null,running=false,wait=0;
 var ARR={up:'M11 20V8.8l-4.6 4.6L5 12l7-7 7 7-1.4 1.4L13 8.8V20z',right:'M5 20v-7a4 4 0 0 1 4-4h7.2l-3.6-3.6L14 4l6 6-6 6-1.4-1.4 3.6-3.6H9a2 2 0 0 0-2 2v7z',left:'M19 20v-7a4 4 0 0 0-4-4H7.8l3.6-3.6L10 4l-6 6 6 6 1.4-1.4L7.8 11H15a2 2 0 0 1 2 2v7z',flag:'M6 21V4h9l.4 2H20v9h-6l-.4-2H8v8z'};
 function dir(a,b){return Math.atan2(b[1]-a[1],b[0]-a[0])}
 function turn(i){var a=dir(P[i-1],P[i]),b=dir(P[i],P[i+1]),x=b-a;while(x>Math.PI)x-=2*Math.PI;while(x<-Math.PI)x+=2*Math.PI;return x>0?'right':'left'}
 function fmtD(m){return m>=1000?(m/1000).toFixed(1).replace('.',',')+' km':(Math.max(10,Math.round(m/10)*10))+' m'}
 function frame(){
  if(wait>0){wait--;if(!wait){pos=0;spd=0}return}
  var goal=pos<60?20:42;spd+=(goal-spd)*.08;
  // frena antes de cada giro
  var acc=0,si=0;for(;si<seg.length;si++){if(pos<acc+seg[si])break;acc+=seg[si]}
  if(si>=seg.length){si=seg.length-1}
  var left=acc+seg[si]-pos;if(left<70&&si<seg.length-1)spd+=(18-spd)*.2;
  pos+=spd*.12;
  if(pos>=tot){pos=tot;wait=40}
  acc=0;si=0;for(;si<seg.length;si++){if(pos<=acc+seg[si])break;acc+=seg[si]}if(si>=seg.length)si=seg.length-1;
  var f=(pos-acc)/seg[si],a=P[si],b=P[si+1],px=a[0]+(b[0]-a[0])*f,py=a[1]+(b[1]-a[1])*f;
  var h=dir(a,b)*180/Math.PI+90;var dh=h-head;while(dh>180)dh-=360;while(dh<-180)dh+=360;head+=dh*.35;
  W.setAttribute('transform','translate('+(CX-px).toFixed(1)+' '+(CY-py).toFixed(1)+')');
  car.style.transform='rotate('+head.toFixed(1)+'deg)';
  var dd='M'+P.slice(0,si+1).map(function(p){return p.join(' ')}).join('L')+'L'+px.toFixed(1)+' '+py.toFixed(1);done.setAttribute('d',dd);
  // instrucción
  var toTurn=(acc+seg[si]-pos)*M,rest=(tot-pos)*M,ic,txt;
  if(pos>=tot){ic='flag';txt=T('Llegaste a {}').replace('{}',DEST);$('#nvDist').textContent=DEST}
  else if(si===seg.length-1){ic='flag';txt=T('Destino: {}').replace('{}',DEST);$('#nvDist').textContent=fmtD(toTurn)}
  else{var tn=turn(si+1);ic=toTurn<260?tn:'up';txt=toTurn<260?T(tn==='right'?'Gira a la derecha en {}':'Gira a la izquierda en {}').replace('{}',NAMES[si+1]):T('Continúa por {}').replace('{}',NAMES[si]);$('#nvDist').textContent=fmtD(toTurn)}
  $('#nvArrow').setAttribute('d',ARR[ic]);$('#nvInstr').textContent=txt;
  var min=Math.max(1,Math.round(rest/1000/30*60));$('#nvEta').textContent=pos>=tot?'':min+' min · '+fmtD(rest);
  $('#nvSpd').textContent=Math.round(pos>=tot?0:spd)}
 whenVisible(scr,function(){if(running)return;running=true;if(reduce){pos=tot*.35;frame();return}timer=setInterval(frame,50)},function(){running=false;clearInterval(timer)});
 document.addEventListener('langchange',frame);
 frame();
})();
})();
