from pathlib import Path
import json,math
root=Path(__file__).resolve().parents[2]
assets=root/'common/src/main/resources/assets/trafficengine'
def read(p):return json.loads(p.read_text(encoding='utf-8'))
checks=0
def check(ok,why):
 global checks;checks+=1
 if not ok:raise AssertionError(why)
variants=read(assets/'blockstates/warning_light.json')['variants']
check(len(variants)==256,'All facing/model/layout/diagonal variants resolve')
for key,entry in variants.items():
 state=dict(pair.split('=') for pair in key.split(','));diagonal=state['diagonal']=='true'
 check(entry['model'].replace('_compact','').endswith('_diagonal')==diagonal,'Blockstate selects the correct model')
 name=entry['model'].split(':')[1];model=read(assets/('models/'+name+'.json'))
 horizontal=state['horizontal']=='true';pair=state['model']!='single';hanging=state['hanging']=='true';compact=state['compact']=='true'
 x=2.5 if pair and horizontal else 5.5;y=2.5 if pair and not horizontal else 5.5
 mount=(7 if hanging else -7) if compact else 0
 shift=(y-2 if hanging else 2-y)+mount
 for e in model['elements']:
  stem=e.get('name')=='sign_post_stem'
  check(('rotation' in e)==(diagonal and not stem),'Only the head turns; square stem stays aligned with ordinary posts')
  if diagonal and not stem:check(e['rotation']=={'origin':[8,8,8],'axis':'y','angle':-45},'Rotation matches existing traffic-light rendering')
  for f in e['faces'].values():
   vertices=f.get('trafficengine_data',{}).get('vertices')
   points=list(zip(vertices[::3],vertices[1::3],vertices[2::3])) if vertices else [(X,Y,Z) for X in (e['from'][0],e['to'][0]) for Y in (e['from'][1],e['to'][1]) for Z in (e['from'][2],e['to'][2])]
   for X,Y,Z in points:
    in_case=x-1e-7<=X<=16-x+1e-7 and y+shift-1e-7<=Y<=16-y+shift+1e-7 and 6.4-1e-7<=Z<=9.5+1e-7
    in_stem=7-1e-7<=X<=9+1e-7 and (14 if hanging else 0)+mount-1e-7<=Y<=(16 if hanging else 2)+mount+1e-7 and 7-1e-7<=Z<=9+1e-7
    check(in_case or in_stem,'Authored model lies within the picking and selection envelopes')
  if stem:
   check(model['textures']['pole']=='trafficengine:block/sign','Stem retains the normal traffic-post texture')
   check(e['to'][1]-e['from'][1]==2,'Mounting stem is short in all layouts')
   check('up' not in e['faces'] and 'down' not in e['faces'],'No coincident caps against the head or support post')
   for face in e['faces'].values():check(face['uv']==([0,9 if hanging else 5,2,11 if hanging else 7] if compact else [0,0 if hanging else 14,2,2 if hanging else 16]),'Stem uses normal post texel density and vertical phase')
  if e.get('name','').startswith('lens_'):
   count=2 if pair else 1;slot=int(e['name'].split('_')[1])
   # Authored lenses go bottom-to-top; BER slots go top-to-bottom in inverted Y.
   render_slot=count-1-slot if pair and not horizontal else slot
   renderer_y=(7+render_slot*6 if pair and not horizontal else 10)-shift
   check(abs(16-renderer_y-e['from'][1])<1e-7 and abs(20-renderer_y-e['to'][1])<1e-7,'Off and animated lenses stay aligned in both mounting modes')
   check(16-(8.6+1.002)<e['from'][2]<6.45<6.5,'Emissive lens, off lens, face and housing keep distinct depth planes')

folder=assets/'models/block/curved_traffic_pole';bare=read(folder/'part_4.json');mounted=read(folder/'part_4_mounted.json')
check(mounted['textures']['metal']=='trafficengine:block/sign','New connector uses the existing post material')
for element in mounted['elements']:
 if element.get('name')!='Aligned mast arm' or 'down' not in element['faces']:continue
 v=element['faces']['down']['trafficengine_data']['vertices'];z=v[2::3]
 check(max(z)<=7+1e-7 or min(z)>=9-1e-7,'Mast underside has no face through the socket opening')
socket=[e for e in mounted['elements'] if e.get('name')!='Aligned mast arm']
check(len(socket)==2,'Only two bounded connector segments')
for e in socket:
 check('up' not in e['faces'] and 'down' not in e['faces'],'No hidden end caps against the arm or attached equipment')
 for side,f in e['faces'].items():
  check(f['texture']=='#metal','Connector texture slot resolves correctly')
  if side in ('east','west'):check(abs(f['uv'][2]-(e['to'][2]-e['from'][2]))<1e-7,'One source texel per model unit along the socket')
  v=f['trafficengine_data']['vertices']
  for X,Y,Z in zip(v[::3],v[1::3],v[2::3]):
   if Y==0:continue
   top=7 if Z<=8.5835921 else 7+(Z-8.5835921)*(6.5501958-7)/(14.5623983-8.5835921)
   check(abs(Y-top)<1e-7,'Socket top follows the actual underside, including the slight bend')
variants=read(assets/'blockstates/curved_traffic_pole.json')['variants']
for facing in ('north','east','south','west'):
 for active in ('false','true'):
  check(f'facing={facing},part=4,lamp_support={active},top_support=false' in variants,'First straight cell resolves both mounting states in every facing')

for part in (4,5):
 for lower in (False,True):
  model=read(assets/f'models/block/curved_traffic_pole/part_{part}_{"both" if lower else "top"}_mounted.json')
  sockets=[e for e in model['elements'] if e.get('name')=='Upper equipment socket']
  check(len(sockets)==(2 if part==4 else 1),'Upper socket follows the actual bend with bounded geometry')
  for e in model['elements']:
   if e.get('name')=='Aligned mast arm' and 'up' in e['faces']:
    zs=e['faces']['up']['trafficengine_data']['vertices'][2::3]
    check(max(zs)<=7+1e-7 or min(zs)>=9-1e-7,'Covered mast top is trimmed; no coplanar socket join')
  for e in sockets:
   check('up' not in e['faces'] and 'down' not in e['faces'],'Upper connector omits both covered end caps')
   for f in e['faces'].values():
    check(f['texture']=='#metal','Upper socket uses the sign-post material')
    points=f['trafficengine_data']['vertices']
    for y,z in zip(points[1::3],points[2::3]):
     if y==16:continue
     expected=9 if part==5 or z<=8.5835921 else 9+(z-8.5835921)*(8.5274977-9)/(14.8628602-8.5835921)
     check(abs(y-expected)<1e-7,'Upper connector lower edge follows the arm exactly')
  for facing in ('north','east','south','west'):
   key=f'facing={facing},part={part},lamp_support={str(lower).lower()},top_support=true'
   check(variants[key]['model'].endswith(f'part_{part}_{"both" if lower else "top"}_mounted'),'Both mounting directions resolve correctly together')

for lang in ('tr_tr','en_us','de_de'):
 d=read(assets/f'lang/{lang}.json')
 for key in ('mode.0','mode.1','mode.2','mode_hint','materials_max','direction.up','direction.down'):
  check(bool(d.get('gui.trafficengine.blueprint.'+key)),'New controls have all three translations')
  check('\ufffd' not in d['gui.trafficengine.blueprint.'+key],'Translation has no replacement characters')
item=read(assets/'models/item/blueprint_tool.json')
check(item['display']['gui']['rotation']==[0,180,0] and item['display']['gui']['scale']==[.96,.96,.96] and item['display']['gui']['translation']==[0,-.48,0],'Inventory icon stays front-facing with a small centered slot margin')
check(item['parent']=='minecraft:block/block','Blueprint icon uses a native baked item model')
check(len(item['elements'])<=18,'Compact item geometry stays bounded')
for e in item['elements']:
 for p in (e['from'],e['to']):check(all(0<=v<=16 for v in p),'Clipboard stays within native model limits')
check(sum(len(e['faces']) for e in item['elements'])<=28,'No extra rendering pass for blueprint appearance')
print('PASS 2.3.2 assets:',checks,'checks; warning models match their envelopes, shared rotations, socket clearance/UVs and complete translations.')
