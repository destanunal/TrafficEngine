"""Checks authored native geometry, mounting interfaces, UV density and inventory projection."""
from pathlib import Path
from itertools import product
import json,math
from GenerateChevronMount233 import arm_polygon

root=Path(__file__).resolve().parents[2]
folder=root/'common/src/main/resources/assets/trafficengine/models/block/chevron_panel'
normals={'east':(1,0,0),'west':(-1,0,0),'up':(0,1,0),'down':(0,-1,0),'north':(0,0,-1),'south':(0,0,1)}
def model(name):return json.loads((folder/(name+'.json')).read_text(encoding='utf-8'))
def support(e):return e['name'].startswith(('carrier_','mount_','clamp_'))
def inside_polygon(x,z,poly):
 return all((q[0]-p[0])*(z-p[1])-(q[1]-p[1])*(x-p[0])>=-1e-7 for p,q in zip(poly,poly[1:]+poly[:1]))
def solid(p,single,diagonal,upper):
 x,y,z=p;bottom=2 if single else 4;bands=[bottom+.75,16-bottom-2.25];stop=bands[1]+.85
 if 7<x<9 and 7<z<9 and 0<y<(16 if upper else stop):return True
 for low in bands:
  if 6.72<x<9.28 and 6.72<z<9.28 and low<y<low+.85:return True
  if low+.1<y<low+.75 and inside_polygon(x,z,arm_polygon(diagonal)):return True
 return False

checked=0
for path in folder.glob('*.json'):
 m=json.loads(path.read_text(encoding='utf-8'))
 if not any(e['name']=='steel_panel' for e in m['elements']):continue
 single=path.stem.startswith('single_');diagonal=path.stem.endswith('_diagonal');bottom=2 if single else 4;top=16-bottom
 steel=next(e for e in m['elements'] if e['name']=='steel_panel')
 assert steel['from']==[2 if single else 0,bottom,5] and steel['to']==[14 if single else 16,top,6]
 assert (steel['to'][0]-steel['from'][0])/(top-bottom)==(1 if single else 2)
 assert m['textures']['metal']==m['textures']['panel']=='trafficengine:block/sign'
 assert steel['faces']['east']['uv']==[0,0,1,top-bottom],'Same edge grain/density as standard traffic sign plates'
 assert steel['faces']['south']['uv']==[0,0,12 if single else 16,top-bottom]
 mounts=[e for e in m['elements'] if e['name'].startswith('mount_')]
 for index,low in enumerate([bottom+.75,top-2.25]):
  arm=[e for e in mounts if e['name'].startswith(f'mount_{index}_')]
  assert abs(min(e['from'][1] for e in arm)-(low+.1))<1e-6
  assert abs(max(e['to'][1] for e in arm)-(low+.75))<1e-6,'Exactly the light mounting stem height, not a solid L-block'
  clamps=[e for e in m['elements'] if e['name'].startswith(f'clamp_{index}_')]
  assert abs(min(e['from'][1] for e in clamps)-low)<1e-6 and abs(max(e['to'][1] for e in clamps)-low-.85)<1e-6
 assert len(m['elements'])<90,'Small static native chunk mesh'
 suffix='_diagonal' if diagonal else '';prefix='single_' if single else '';stop=top-1.4
 for upper in [False,True]:
  extra=model(prefix+('upper_carrier' if upper else 'carrier_cap')+suffix)['elements']
  assert not any(e['name'].startswith(('mount_','clamp_')) for e in extra),'Arms/clamps never change with upper pole attachment'
  if not upper:
   assert len(extra)==1 and extra[0]['from']==[7,stop,7] and extra[0]['to']==[9,stop,9]
  es=[e for e in m['elements'] if support(e)]+extra
  for e in es:
   assert 'rotation' not in e,'Mast/collars stay square while short arms follow the panel'
   for n,face in e['faces'].items():
    assert face['texture']=='#metal'
    raw=face['trafficengine_data']['vertices'];p=[raw[i:i+3] for i in range(0,12,3)]
    a=[p[1][i]-p[0][i] for i in range(3)];b=[p[2][i]-p[0][i] for i in range(3)]
    cross=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];length=math.sqrt(sum(v*v for v in cross))
    assert length>1e-8 and sum(cross[i]*normals[n][i] for i in range(3))>1e-8
    center=[sum(v[i] for v in p)/4 for i in range(3)];normal=[v/length for v in cross]
    outside=[center[i]+normal[i]*.0002 for i in range(3)];inside=[center[i]-normal[i]*.0002 for i in range(3)]
    assert not solid(outside,single,diagonal,upper),(path.name,e['name'],'covered/internal face causes flicker')
    assert solid(inside,single,diagonal,upper),(path.name,e['name'],'face detached from the mounting structure')
    checked+=1

# Every finite block-state variant selects exactly one matching cap/extension.
bs=json.loads((root/'common/src/main/resources/assets/trafficengine/blockstates/chevron_panel.json').read_text())
for single,diagonal,upper,facing in product([False,True],[False,True],[False,True],['north','east','south','west']):
 parts=[p for p in bs['multipart'] if p['when'].get('upper_connection')==str(upper).lower() and p['when'].get('single')==str(single).lower() and p['when'].get('diagonal')==str(diagonal).lower() and p['when'].get('facing')==facing]
 assert len(parts)==1
 assert parts[0]['apply']['model'].endswith(('single_' if single else '')+('upper_carrier' if upper else 'carrier_cap')+('_diagonal' if diagonal else ''))

item=json.loads((root/'common/src/main/resources/assets/trafficengine/models/item/chevron_panel.json').read_text())
gui=item['display']['gui'];assert gui['rotation']==[30,225,0] and gui['scale']==[.65,.65,.65]
rx,ry=map(math.radians,gui['rotation'][:2])
for e in item['elements']:
 points=list(product(*zip(e['from'],e['to'])))
 for face in e['faces'].values():
  raw=face.get('trafficengine_data',{}).get('vertices',[]);points.extend(zip(raw[::3],raw[1::3],raw[2::3]))
 for x,y,z in points:
  x-=8;y-=8;z-=8
  x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry)
  y=y*math.cos(rx)-z*math.sin(rx)
  assert max(abs(x*.65),abs(y*.65))<=7.5,'Inventory clipping'
print(f'PASS Chevron233: {checked} exterior support faces, two light-style mounts, panel UV density, conditional connections and inventory fit.')
