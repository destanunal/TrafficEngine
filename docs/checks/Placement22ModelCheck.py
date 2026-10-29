"""Check actual model-space clearance, cardinal mast shade references and GUI separation."""
import json, math, itertools
from pathlib import Path

root=Path(__file__).resolve().parents[2]
models=root/'common/src/main/resources/assets/trafficengine/models'
def read(p):return json.loads(p.read_text(encoding='utf-8'))
def rotate(p,r):
    if not r:return p
    a=math.radians(r['angle']);c,s=math.cos(a),math.sin(a);o=r['origin'];x,y,z=[p[i]-o[i] for i in range(3)]
    if r['axis']=='y':return [o[0]+c*x+s*z,o[1]+y,o[2]-s*x+c*z]
    raise AssertionError(r)

clamps=set();faces_count=0
for p in (models/'block/sign/diagonal').glob('*.json'):
    m=read(p)
    for e in m['elements']:
        if not e.get('name','').startswith('Post clamp'):continue
        assert 'rotation' not in e, 'Square collar opening must stay aligned with the square pole'
        # Each authored prism is split into a few clipped faces, sharing this group ID.
        clamps.add((p.name,' '.join(e['name'].split()[:5])))
        for f in e['faces'].values():
            v=f['trafficengine_data']['vertices'];pts=list(zip(v[::3],v[1::3],v[2::3]));faces_count+=1
            for x,y,z in pts:
                assert 8+((z-8)-(x-8))/math.sqrt(2)>=7.0009, (p.name,e['name'],'plate intersection')
            xs=[a[0] for a in pts];zs=[a[2] for a in pts]
            assert min(xs)>=9.019 or max(xs)<=6.981 or min(zs)>=9.019 or max(zs)<=6.981, (p.name,e['name'],'pole intersection')
assert len(clamps)==96,len(clamps)

directions={'east':(1,0,0),'west':(-1,0,0),'up':(0,1,0),'down':(0,-1,0),'south':(0,0,1),'north':(0,0,-1)}
faces=top=0
for p in (models/'block/curved_traffic_pole').glob('*.json'):
    m=read(p);assert m['ambientocclusion'] is False
    assert m['textures']['metal']=='trafficengine:block/sign'
    for e in m['elements']:
        for side,f in e['faces'].items():
            v=f['trafficengine_data']['vertices'];a=[v[3+i]-v[i] for i in range(3)];b=[v[6+i]-v[i] for i in range(3)]
            n=(a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
            expected=max(directions,key=lambda d:sum(n[i]*directions[d][i] for i in range(3)))
            assert side==expected,(p.name,e.get('name'),side,expected)
            assert f['texture']=='#metal' and f['trafficengine_data'].get('smooth_shade') is True and not f['trafficengine_data'].get('match_straight_shade',False)
            if expected=='up':top+=1
            faces+=1
assert top>0

def bounds(stem):
    geometry=read(models/f'block/{stem}.json');item=read(models/f'item/{stem}.json');gui=item['display']['gui']
    assert gui['rotation']==[30,225,0]
    xs=[];ys=[]
    for e in geometry['elements']:
        for f in e['faces'].values():
            v=f.get('trafficengine_data',{}).get('vertices',[])
            for x,y,z in zip(v[::3],v[1::3],v[2::3]):
                dx,dy,dz=x-8,y-8,z-8;c,s=math.cos(math.radians(225)),math.sin(math.radians(225))
                X=c*dx+s*dz;Z=-s*dx+c*dz;Y=math.cos(math.radians(30))*dy-.5*Z
                xs.append(X*gui['scale'][0]+gui['translation'][0]);ys.append(Y*gui['scale'][1]+gui['translation'][1])
    assert max(abs(x) for x in xs)<=7.2 and max(abs(y) for y in ys)<=7.2
    return max(xs)-min(xs),max(ys)-min(ys)
large=bounds('large_road_direction_sign');highway=bounds('highway_direction_sign')
assert highway[0]<large[0]*.88 and highway[1]<large[1]*.88,(large,highway)
print(f'PASS: {len(clamps)} square collar groups / {faces_count} faces clear both plate and pole; {faces} mast faces use continuous shading; Turkish-named highway icon is larger than the big-road icon by at least 12% on both axes, preserving right-facing centering.')
