"""Assembled front/back collar clearance for every shape, facing and placement."""
import json,math,itertools
from pathlib import Path
assets=Path(__file__).resolve().parents[2]/'common/src/main/resources/assets/trafficengine'
def read(p):return json.loads(p.read_text(encoding='utf-8'))
state=read(assets/'blockstates/double_traffic_sign.json');shape_refs={};face_count=0;assembled=0
for part in state['multipart']:
    when=part['when'];side='shape' if 'shape' in when else 'back_shape';key=(when['shape' if side=='shape' else 'back_shape'],when['facing'],when['diagonal'],side)
    path=part['apply']['model'];assert '/sign/double/' in path
    shape_refs[key]=(read(assets/('models/'+path.split(':')[1]+'.json')),part['apply'].get('y',0))
for diagonal in (False,True):
    mode='diagonal' if diagonal else 'straight';directory=assets/'models/block/sign/double'/mode
    for path in directory.glob('*.json'):
        m=read(path);original=read(assets/'models/block/sign'/('diagonal' if diagonal else '')/path.name)
        hardware=[e for e in m['elements'] if e.get('name','').startswith('Post clamp')]
        assert [e for e in m['elements'] if e not in hardware]==[e for e in original['elements'] if not e.get('name','').startswith('Post clamp')],path
        assert sum(len(e['faces']) for e in hardware)<=48
        for e in hardware:
            assert 'rotation' not in e
            for f in e['faces'].values():
                face_count+=1;v=f['trafficengine_data']['vertices'];points=list(zip(v[::3],v[1::3],v[2::3]))
                for x,y,z in points:
                    depth=8+(z-x)/math.sqrt(2) if diagonal else z
                    assert 7.0009<=depth<=8.9991,(path,e['name'],depth)
                xs=[p[0] for p in points];zs=[p[2] for p in points]
                assert min(xs)>=9.019 or max(xs)<=6.981 or min(zs)>=9.019 or max(zs)<=6.981
    names=[p.stem for p in directory.glob('*.json')];assert len(names)==12
    for front,back,facing in itertools.product(names,names,('north','east','south','west')):
        for side,shape in [('shape',front),('back_shape',back)]:
            m,rotation=shape_refs[shape,facing,str(diagonal).lower(),side]
            for e in m['elements']:
                if not e.get('name','').startswith('Post clamp'):continue
                # Normalize the cardinal block-state rotation, including the back's 180-degree turn.
                direction={'north':0,'east':90,'south':180,'west':270}[facing]
                a=math.radians(rotation-direction);c,s=math.cos(a),math.sin(a)
                for f in e['faces'].values():
                    v=f['trafficengine_data']['vertices']
                    for x,y,z in zip(v[::3],v[1::3],v[2::3]):
                        X=8+c*(x-8)-s*(z-8);Z=8+s*(x-8)+c*(z-8)
                        depth=8+(Z-X)/math.sqrt(2) if diagonal else Z
                        assert 7.0009<=depth<=8.9991,(front,back,facing,side,depth)
        assembled+=1
print(f'PASS: {assembled} assembled front/back shape/facing/orientation combinations; {face_count} collar faces stay between both plates and outside the pole; original plate/post geometry is preserved and quad count never increases.')
