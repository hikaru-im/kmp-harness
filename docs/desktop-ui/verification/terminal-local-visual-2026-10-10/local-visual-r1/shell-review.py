from pathlib import Path
import hashlib, json, re
from collections import defaultdict
from PIL import Image, ImageDraw

root = Path('/tmp/harness-terminal-local-visual-evidence-20261010')
captures = root / 'shell-captures'
output = root / 'contact-sheets'
output.mkdir(exist_ok=True)
files = sorted(captures.glob('*.png'))
assert len(files) == len(list(captures.glob('*.geometry.txt'))) == 80
groups = defaultdict(list)
rows = []
for p in files:
    match = re.match(r'^(1440x900|1024x768|600x800)-(light|dark)(-navigator-resized)?-(.+)$', p.stem)
    assert match, p
    width, height = map(int, match[1].split('x'))
    text = p.with_suffix('.geometry.txt').read_text()
    assert f'viewport={width}x{height} theme={match[2]} state={match[4]}' in text
    assert 'actualOverlay=' in text
    with Image.open(p) as im:
        assert im.size == (width, height)
    key = match[1] + '-' + match[2] + (match[3] or '')
    groups[key].append(p)
    rows.append(dict(name=p.name, sha256=hashlib.sha256(p.read_bytes()).hexdigest(),
        dimensions=[width,height], theme=match[2], state=match[4]))
assert len(groups) == 8 and all(len(values) == 10 for values in groups.values())
sheets = []
for key, values in sorted(groups.items()):
    sheet = Image.new('RGB', (740, 1380), '#dedede')
    draw = ImageDraw.Draw(sheet)
    for index,p in enumerate(values):
        x = 10 + (index % 2) * 370
        y = 10 + (index // 2) * 276
        draw.text((x,y), p.stem.replace(key+'-', ''), fill='black')
        with Image.open(p) as im:
            preview = im.convert('RGB')
            preview.thumbnail((350,246))
            sheet.paste(preview, (x, y+20))
    name = key + '.png'
    sheet.save(output/name)
    sheets.append(name)
(root/'shell-capture-review.json').write_text(json.dumps(dict(passed=True, png_count=80,
    geometry_count=80, contact_sheets=sheets, files=rows), ensure_ascii=False, indent=2)+chr(10))
print(json.dumps(dict(captures=80, contact_sheets=sheets)))
