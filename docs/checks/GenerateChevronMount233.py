"""Offline native-model authoring. Light-style short arms and square clamps; no runtime mesh work."""
from pathlib import Path
from copy import deepcopy
import json, math

def clip(poly,axis,bound,above):
 out=[]
 for a,b in zip(poly,poly[1:]+poly[:1]):
  av=(a[axis]-bound)*(1 if above else -1);bv=(b[axis]-bound)*(1 if above else -1)
  if av>=-1e-8:out.append(a)
  if (av>=0)!=(bv>=0):
   t=av/(av-bv);out.append(tuple(a[i]+(b[i]-a[i])*t for i in range(2)))
 clean=[]
 for p in out:
  if not clean or math.dist(p,clean[-1])>1e-7:clean.append(p)
 if len(clean)>1 and math.dist(clean[0],clean[-1])<1e-7:clean.pop()
 return clean

def surface(name,p,normal,uv):
 return {'name':name,'from':[round(min(v[i] for v in p),8) for i in range(3)],'to':[round(max(v[i] for v in p),8) for i in range(3)],
  'faces':{normal:{'texture':'#metal','uv':[round(v,8) for v in uv],'trafficengine_data':{'vertices':[round(v,8) for point in p for v in point],'match_straight_shade':True}}}}

def side(name,n,x0,y0,z0,x1,y1,z1,uv):
 if n=='north':p=[(x1,y1,z0),(x1,y0,z0),(x0,y0,z0),(x0,y1,z0)]
 elif n=='south':p=[(x0,y1,z0),(x0,y0,z0),(x1,y0,z0),(x1,y1,z0)]
 elif n=='west':p=[(x0,y1,z0),(x0,y0,z0),(x0,y0,z1),(x0,y1,z1)]
 elif n=='east':p=[(x0,y1,z1),(x0,y0,z1),(x0,y0,z0),(x0,y1,z0)]
 elif n=='up':p=[(x0,y0,z0),(x0,y0,z1),(x1,y0,z1),(x1,y0,z0)]
 else:p=[(x0,y0,z1),(x0,y0,z0),(x1,y0,z0),(x1,y0,z1)]
 return surface(name,p,n,uv)

def arm_polygon(diagonal):
 raw=[(7.4,6),(8.6,6),(8.6,7.3),(7.4,7.3)]
 if not diagonal:return raw
 c=math.sqrt(.5)
 return [(8+c*(x-8)-c*(z-8),8+c*(x-8)+c*(z-8)) for x,z in raw]

def mount_geometry(single,diagonal):
 bottom=2 if single else 4;top=16-bottom
 bands=[bottom+.75,top-2.25];stop=bands[1]+.85
 body=[];a,b=6.72,9.28;raw=arm_polygon(diagonal)
 def wall(name,n,lo,hi,start,end,outer=False):
  if hi-lo<1e-7 or end-start<1e-7:return
  edge=(b if n in ['east','south'] else a) if outer else (9 if n in ['east','south'] else 7)
  x0,x1=(start,end) if n in ['north','south'] else (edge,edge)
  z0,z1=(edge,edge) if n in ['north','south'] else (start,end)
  body.append(side(name,n,x0,lo,z0,x1,hi,z1,[0,16-hi,end-start,16-lo]))
 # Pole sides stop inside the panel, with no covered surfaces under either clamp.
 for n in ['north','south','east','west']:
  for lo,hi in [(0,bands[0]),(bands[0]+.85,bands[1])]:wall('carrier_'+n,n,lo,hi,7,9)
 body.append(side('carrier_down','down',7,0,7,9,0,9,[0,0,2,2]))
 for index,low in enumerate(bands):
  high=low+.85;slo=low+.1;shi=low+.75
  # Only cut a clamp wall where its matching short arm actually enters it.
  for n in ['north','south','east','west']:
   axis=1 if n in ['north','south'] else 0;edge=a if n in ['north','west'] else b
   intersections=[]
   for p,q in zip(raw,raw[1:]+raw[:1]):
    if min(p[axis],q[axis])-1e-8<=edge<=max(p[axis],q[axis])+1e-8 and abs(p[axis]-q[axis])>1e-8:
     t=(edge-p[axis])/(q[axis]-p[axis]);v=p[1-axis]+t*(q[1-axis]-p[1-axis])
     intersections.append(v)
   start,end=(max(a,min(intersections)),min(b,max(intersections))) if len(intersections)>=2 else (a,a)
   if end-start>1e-7:
    for lo,hi,u,v in [(low,slo,a,b),(shi,high,a,b),(slo,shi,a,start),(slo,shi,end,b)]:wall(f'clamp_{index}_{n}',n,lo,hi,u,v,True)
   else:wall(f'clamp_{index}_{n}',n,low,high,a,b,True)
  # Annular ledges meet the square mast without inner/copanar faces.
  for n,y in [('down',low),('up',high)]:
   for k,(x0,z0,x1,z1) in enumerate([(a,a,b,7),(a,9,b,b),(a,7,7,9),(9,7,b,9)]):
    body.append(side(f'clamp_{index}_{n}_{k}',n,x0,y,z0,x1,y,z1,[0,0,x1-x0,z1-z0]))
  # Clip the arm against the outer clamp instead of passing a thick block through the post.
  polys=[clip(raw,0,b,True),clip(clip(raw,0,b,False),1,a,False)] if diagonal else [[(7.4,6),(8.6,6),(8.6,a),(7.4,a)]]
  for pi,poly in enumerate(polys):
   if len(poly)<3:continue
   quads=[poly] if len(poly)<=4 else [[poly[0],poly[t],poly[t+1]] for t in range(1,len(poly)-1)]
   for t,quad in enumerate(quads):
    for n,y,points in [('up',shi,list(reversed(quad))),('down',slo,quad)]:
     points=points if len(points)==4 else points+[points[-1]]
     local=[(8+math.sqrt(.5)*(x-8)+math.sqrt(.5)*(z-8),8-math.sqrt(.5)*(x-8)+math.sqrt(.5)*(z-8)) if diagonal else (x,z) for x,z in points]
     body.append(surface(f'mount_{index}_{pi}_{n}_{t}',[(x,y,z) for x,z in points],n,[0,0,max(p[0] for p in local)-min(p[0] for p in local),max(p[1] for p in local)-min(p[1] for p in local)]))
   for k,(p,q) in enumerate(zip(poly,poly[1:]+poly[:1])):
    if abs(p[0]-b)<1e-7 and abs(q[0]-b)<1e-7:continue
    if abs(p[1]-a)<1e-7 and abs(q[1]-a)<1e-7:continue
    inv=lambda p:8-math.sqrt(.5)*(p[0]-8)+math.sqrt(.5)*(p[1]-8) if diagonal else p[1]
    if abs(inv(p)-6)<1e-6 and abs(inv(q)-6)<1e-6:continue
    dx,dz=q[0]-p[0],q[1]-p[1]
    normal='east' if abs(dz)>abs(dx) and dz>0 else 'west' if abs(dz)>abs(dx) else 'north' if dx>0 else 'south'
    body.append(surface(f'mount_{index}_{pi}_side_{k}',[(q[0],shi,q[1]),(q[0],slo,q[1]),(p[0],slo,p[1]),(p[0],shi,p[1])],normal,[0,0,math.hypot(dx,dz),.65]))
 cap=side('carrier_up','up',7,stop,7,9,stop,9,[0,0,2,2])
 # The top clamp covers the post up to stop; extend above it only when connected.
 upper=[]
 for n in ['north','south','east','west']:
  x0,x1=(7,9) if n in ['north','south'] else ((9,9) if n=='east' else (7,7))
  z0,z1=(7,7) if n=='north' else ((9,9) if n=='south' else (7,9))
  upper.append(side('carrier_'+n,n,x0,stop,z0,x1,16,z1,[0,0,2,16-stop]))
 return body,upper,[cap]

def panel_uv(e):
 x0,y0,z0=e['from'];x1,y1,z1=e['to'];w,h,d=x1-x0,y1-y0,z1-z0
 for normal,f in e['faces'].items():
  f['texture']='#panel'
  f.pop('rotation',None)
  if normal in ['north','south']:f['uv']=[0,0,w,h]
  elif normal in ['east','west']:f['uv']=[0,0,d,h]
  else:f['uv']=[0,0,d,w];f['rotation']=90

def generate(root):
 folder=root/'common/src/main/resources/assets/trafficengine/models/block/chevron_panel'
 for path in list(folder.glob('*.json')):
  if path.stem.startswith(('carrier_cap','upper_carrier','single_carrier_cap','single_upper_carrier')):continue
  m=json.loads(path.read_text(encoding='utf-8'));single=path.stem.startswith('single_');diag=path.stem.endswith('_diagonal')
  m['textures']['panel']='trafficengine:block/sign'
  m['elements']=[e for e in m['elements'] if not e['name'].startswith(('carrier_','mount_','clamp_'))]
  for e in m['elements']:
   if e['name']=='steel_panel' or e['name'].endswith('_cap'):panel_uv(e)
  if any(e['name']=='steel_panel' for e in m['elements']):m['elements']+=mount_geometry(single,diag)[0]
  path.write_text(json.dumps(m,separators=(',',':'))+'\n',encoding='utf-8')
 for single in [False,True]:
  for diag in [False,True]:
   _,upper,cap=mount_geometry(single,diag)
   for name,es in [('upper_carrier',upper),('carrier_cap',cap)]:
    name=('single_' if single else '')+name+('_diagonal' if diag else '')
    m={'ambientocclusion':False,'textures':{'metal':'trafficengine:block/sign'},'elements':es}
    (folder/f'{name}.json').write_text(json.dumps(m,separators=(',',':'))+'\n',encoding='utf-8')
 # Same authored standalone body and end caps in the inventory.
 item=root/'common/src/main/resources/assets/trafficengine/models/item/chevron_panel.json'
 m=json.loads(item.read_text(encoding='utf-8'));body=json.loads((folder/'red_white_right.json').read_text())
 m['textures']=body['textures'];m['elements']=body['elements']+json.loads((folder/'left_cap.json').read_text())['elements']+json.loads((folder/'right_cap.json').read_text())['elements']+mount_geometry(False,False)[2]
 item.write_text(json.dumps(m,separators=(',',':'))+'\n',encoding='utf-8')
 bs=root/'common/src/main/resources/assets/trafficengine/blockstates/chevron_panel.json'
 m=json.loads(bs.read_text(encoding='utf-8'));m['multipart']=[p for p in m['multipart'] if 'upper_connection' not in p['when']]
 for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:
  for single in [False,True]:
   for diag in [False,True]:
    for upper in [False,True]:
     name=('single_' if single else '')+('upper_carrier' if upper else 'carrier_cap')+('_diagonal' if diag else '')
     m['multipart'].append({'when':{'facing':facing,'single':str(single).lower(),'diagonal':str(diag).lower(),'upper_connection':str(upper).lower()},'apply':{'model':'trafficengine:block/chevron_panel/'+name,'y':angle}})
 bs.write_text(json.dumps(m,separators=(',',':'))+'\n',encoding='utf-8')

if __name__=='__main__':generate(Path(__file__).resolve().parents[2])
