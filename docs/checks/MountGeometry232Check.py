"""Check native UV density/phase and the stationary chevron carrier without a game window."""
from pathlib import Path
import json,math
root=Path(__file__).resolve().parents[2];assets=root/'common/src/main/resources/assets/trafficengine';checks=0
def read(p):return json.loads(p.read_text(encoding='utf-8'))
def check(value,why):
 global checks;checks+=1
 if not value:raise AssertionError(why)
def area(poly):return abs(sum(a[0]*b[1]-a[1]*b[0] for a,b in zip(poly,poly[1:]+poly[:1])))/2
def local(p):
 x,z=p;return [8+((x-8)+(z-8))/math.sqrt(2),8+((z-8)-(x-8))/math.sqrt(2)]
for p in (assets/'models/block/chevron_panel').glob('*.json'):
 m=read(p);post=next((e for e in m.get('elements',[]) if e.get('name')=='post'),None)
 if not post:continue
 check('rotation' not in post,'Chevron lower post must match the square traffic post')
 check(post['from']==[7,0,7] and post['to']==[9,6,9],'Chevron stem retains the existing width and height')
 check(m['textures']['metal']=='trafficengine:block/sign','Chevron uses the normal pole material')
 check('up' not in post['faces'] and 'down' not in post['faces'],'Covered carrier caps are omitted')
 for face in post['faces'].values():check(face['uv']==[0,10,2,16],'Stem retains traffic-post texture density and vertical phase')
 if '_diagonal' not in p.stem:continue
 caps=[e for e in m['elements'] if e.get('name','').startswith('exposed_post_cap')]
 check(0<len(caps)<=8,'Only a bounded number of exposed cap faces are needed')
 total=0
 for e in caps:
  v=e['faces']['up']['trafficengine_data']['vertices'];poly=list(zip(v[::3],v[2::3]));total+=area(poly)
  a=[v[i+3]-v[i] for i in range(3)];b=[v[i+6]-v[i] for i in range(3)]
  check(a[2]*b[0]-a[0]*b[2]>0,'Exposed cap faces point upward')
  # Interior samples may touch edges but never overlap either covered support footprint.
  for i in range(1,30):
   t=i/30;point=[poly[0][k]*(1-t)+(sum(q[k] for q in poly)/len(poly))*t for k in (0,1)];x,z=local(point)
   check(not (7+1e-6<z<8-1e-6 or (7+1e-6<x<9-1e-6 and 8+1e-6<z<9-1e-6)),'Visible pole cap is trimmed against panel and rear bracket; no coplanar overlap')
 check(0<total<4,'Only the uncovered fraction of the square top cap is drawn')

offsets={0:(0,0),3:(16,-16),4:(16,-32),5:(16,-48)};joints={};count=0
for p in (assets/'models/block/curved_traffic_pole').glob('part_*.json'):
 part=int(p.stem.split('_')[1]);m=read(p)
 check(m['textures']['metal']=='trafficengine:block/sign','Every curved surface uses normal sign-post texture')
 if part not in offsets:continue
 oy,oz=offsets[part]
 for e in m['elements']:
  if e.get('name')!='Aligned mast arm':continue
  for side,f in e['faces'].items():
   d=f['trafficengine_data'];v=d['vertices'];points=[v[i:i+3] for i in range(0,12,3)];uv=f['uv'];count+=1
   check(d.get('smooth_shade') and not d.get('match_straight_shade'),'Previous continuous lighting is preserved')
   check(all(-1e-7<=a<=16+1e-7 for a in uv),'UV never samples neighbouring atlas sprites')
   check(abs(abs(uv[2]-uv[0])-2)<1e-7,'Normal 2-unit pole width uses two source texels')
   check(abs(abs(uv[3]-uv[1])-math.dist(points[0],points[1]))<2e-5,'One source texel per unit along each curved surface')
   if side not in ('up','down','north','south'):continue
   family='outer' if side in ('up','south') else 'inner';values=[uv[1],uv[3],uv[3],uv[1]]
   for point,value in zip(points,values):
    k=(family,round(point[0],5),round(point[1]+oy,5),round(point[2]+oz,5));phase=value%16
    if k in joints:
     delta=abs(joints[k]-phase);check(min(delta,16-delta)<3e-5,'Texture phase continues across mast segments, chunk cells and wrap cuts')
    joints[k]=phase
    if part==5:check(min(abs((point[2]+oz-value)%16),abs((value-point[2]-oz)%16))<3e-5,'Straight top/bottom faces match ordinary horizontal sign-post mapping')
check(count<=192,'Texture continuity adds bounded baked geometry rather than frame-time work')
import itertools
multipart=read(assets/'blockstates/traffic_sign_post.json')['multipart']
def matches(condition,state):
 return all(any(matches(c,state) for c in value) if prop=='OR' else all(matches(c,state) for c in value) if prop=='AND' else str(state.get(prop)).lower()==str(value).lower() for prop,value in condition.items())
directions=('north','south','east','west','up','down')
for axis in ('x','y','z'):
 for flags in itertools.product((False,True),repeat=6):
  state=dict(zip(directions,flags));state['axis']=axis;arms=[]
  for part in multipart:
   if not matches(part.get('when',{}),state):continue
   a=part['apply'];a=a[0] if isinstance(a,list) else a;model=a['model'].split('/')[-1]
   if model=='traffic_sign_post_double':arms+=['up','down'] if a.get('x')==90 else ['east','west'] if a.get('y')==90 else ['north','south']
   elif model=='traffic_sign_post_connection':arms.append('up' if a.get('x')==270 else 'down' if a.get('x')==90 else {0:'north',90:'east',180:'south',270:'west'}[a.get('y',0)] if a.get('x')!=180 else 'south')
  expected={side for side,flag in zip(directions,flags) if flag}
  pair={'x':{'east','west'},'y':{'up','down'},'z':{'north','south'}}[axis]
  if not (expected-pair):expected|=pair
  check(set(arms)==expected,'Post model union retains its original axis/connection geometry')
  check(len(arms)==len(set(arms)),'Each post arm is baked exactly once, including compact warning mounts')
print('PASS compact/chevron/mast models:',checks,'checks;',count,'bounded mast surface faces; exact post texel density, continuous UV phase and trimmed cap clearance.')
