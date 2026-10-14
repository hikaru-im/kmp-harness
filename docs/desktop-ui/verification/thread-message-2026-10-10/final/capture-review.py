from pathlib import Path
import hashlib
import json
import re
from PIL import Image, ImageDraw

ROOT = Path('/tmp/harness-thread-message-final-evidence-20261010')
def rect(raw):
    return tuple(float(v) for v in re.fullmatch(r'Rect.fromLTRB\(([^)]+)\)', raw).group(1).split(','))
def inside(box, outer):
    return box[0] >= outer[0]-1 and box[1] >= outer[1]-1 and box[2] <= outer[2]+1 and box[3] <= outer[3]+1
rows=[]
message_matrix=set()
for png in sorted((ROOT/'message-captures').glob('*.png')):
    proof=json.loads(png.with_suffix('.geometry.json').read_text())
    assert proof['schema']=='harness.thread-message.capture.v1'
    assert proof['sha256']==hashlib.sha256(png.read_bytes()).hexdigest()
    assert proof['image']==png.name and proof['density']==proof['fontScale']==1
    dims=proof['width'],proof['height']
    with Image.open(png) as im: assert im.size==dims
    assert rect(proof['rootBoundsInWindow'])==(0,0,*dims)
    message_matrix.add((*dims,proof['theme'],proof['stage']))
    nodes={node['tag']:node for node in proof['nodes']}
    viewport=rect(nodes['live-thread-timeline']['boundsInRoot'])
    for node in nodes.values():
        if node['tag'].startswith('message:'):
            clip=rect(node['boundsInRoot'])
            if clip[2]>clip[0] and clip[3]>clip[1]: assert inside(clip,viewport),(png,node['tag'])
    for text in proof['textLayout']:
        bounds=rect(text['nodeBounds']);w=bounds[2]-bounds[0];h=bounds[3]-bounds[1]
        assert text['lastLineEnd']==text['expectedLength']
        assert text['lineCount']==len(text['lines']) and text['overflow']=='Visible'
        assert text['maxLines']==2147483647
        assert all(not line['ellipsized'] and inside(tuple(line[k] for k in ('left','top','right','bottom')),(0,0,w,h)) for line in text['lines'])
        assert all(char['left']>=-1 and char['right']<=w+1 for char in text['characterBounds'])
    rows.append({'image':str(png.relative_to(ROOT)),'sha256':proof['sha256'],'geometry':str(png.with_suffix('.geometry.json').relative_to(ROOT)),'dimensions':dims})
assert message_matrix=={(w,h,t,s) for w,h in [(600,800),(1440,900)] for t in ['Light','Dark'] for s in ['collapsed','expanded','long-content']}
shell_groups={}
for png in sorted((ROOT/'shell-captures').glob('*.png')):
    geo=png.with_suffix('.geometry.txt');raw=geo.read_text()
    match=re.match(r'viewport=(\d+)x(\d+) theme=(light|dark) state=(.*)',raw)
    assert match,(png,raw[:100])
    w,h=int(match[1]),int(match[2]);theme,stage=match[3],match[4]
    with Image.open(png) as im: assert im.size==(w,h)
    assert png.name.endswith('-'+stage+'.png') and png.name.startswith(f'{w}x{h}-{theme}')
    group=png.name.removesuffix('-'+stage+'.png');shell_groups.setdefault(group,[]).append(png)
    box={tag:tuple(map(float,coords.split(','))) for tag,coords in re.findall(r'(?m)^([^=\n]+)=Rect.fromLTRB\(([^)]+)\)$',raw)}
    assert inside(box['main-surface'],(0,0,w,h))
    if stage=='navigation-after-panel': assert 'live-composer-editor' not in box
    else: assert inside(box['live-composer-editor'],(0,0,w,h))
    rows.append({'image':str(png.relative_to(ROOT)),'sha256':hashlib.sha256(png.read_bytes()).hexdigest(),'geometry':str(geo.relative_to(ROOT)),'dimensions':[w,h]})
assert len(shell_groups)==8 and all(len(ps)==10 for ps in shell_groups.values())
def sheet(paths,name,columns,maxsize):
    thumbs=[]
    for p in paths:
        im=Image.open(p).convert('RGB');im.thumbnail(maxsize)
        tile=Image.new('RGB',(maxsize[0],maxsize[1]+34),'#777777');tile.paste(im,((maxsize[0]-im.width)//2,34));ImageDraw.Draw(tile).text((6,8),p.name,fill='white');thumbs.append(tile)
    sheet=Image.new('RGB',(columns*maxsize[0],((len(thumbs)+columns-1)//columns)*(maxsize[1]+34)),'#777777')
    for i,im in enumerate(thumbs): sheet.paste(im,((i%columns)*maxsize[0],(i//columns)*(maxsize[1]+34)))
    sheet.save(ROOT/name)
for dims in ['600x800','1440x900']:
    sheet(sorted((ROOT/'message-captures').glob('*'+dims+'*.png')),'message-contact-'+dims+'.png',3,(480,640))
for group,ps in shell_groups.items(): sheet(ps,'shell-contact-'+group+'.png',5,(360,300))
(ROOT/'capture-metadata-review.json').write_text(json.dumps({'status':'passed','message_pngs':12,'shell_pngs':80,'rows':rows,'evidence_limit':'Offscreen production surface; character STRUT/TIGHT bounds are layout evidence, no original pixel, native or remote proof'},indent=2)+'\n')
print('Capture metadata passed: 12 message + 80 shell PNGs; 10 contact sheets generated')
