"""Small tone-matched, vector-style emblem and plate atlas. No source photos are bundled."""
from pathlib import Path
from xml.etree import ElementTree

from PIL import Image, ImageChops, ImageDraw, ImageFont
from fontTools.pens.basePen import BasePen
from fontTools.svgLib.path import parse_path

from .mesh import ATLAS, mix

FONT = Path(__file__).with_name('lettering.ttf')
RESAMPLE = Image.Resampling.LANCZOS


class Outline(BasePen):
    def __init__(self):
        super().__init__(None)
        self.rings = []

    def _moveTo(self, p): self.rings.append([p])
    def _lineTo(self, p): self.rings[-1].append(p)
    def _closePath(self): pass
    def _endPath(self): pass

    def _curveToOne(self, b, c, d):
        a = self.rings[-1][-1]
        for i in range(1, 13):
            t = i/12
            inv = 1-t
            self.rings[-1].append(tuple(inv**3*p + 3*inv*inv*t*q + 3*inv*t*t*r + t**3*s
                                       for p, q, r, s in zip(a, b, c, d)))


def gradient(size, stops):
    image = Image.new('RGBA', size)
    draw = ImageDraw.Draw(image)
    for y in range(size[1]):
        t = y / max(1, size[1]-1)
        for (a, start), (b, end) in zip(stops, stops[1:]):
            if t <= b:
                fill = tuple(round(mix(p, q, (t-a)/(b-a))) for p, q in zip(bytes.fromhex(start), bytes.fromhex(end))) + (255,)
                draw.line((0,y,size[0],y), fill=fill)
                break
    return image


def lettering(text, width, height, slant=False):
    font = ImageFont.truetype(str(FONT), 150)
    l,t,r,b = font.getbbox(text)
    mask = Image.new('L', (r-l+8, b-t+8))
    ImageDraw.Draw(mask).text((4-l, 4-t), text, font=font, fill=255)
    mask = mask.crop(mask.getbbox())
    if slant:
        shear = .23
        mask = mask.transform((mask.width + int(mask.height*shear) + 1, mask.height), Image.Transform.AFFINE,
                              (1, shear, -mask.height*shear, 0, 1, 0), resample=Image.Resampling.BICUBIC)
    return mask.resize((width, height), RESAMPLE)


def stamp(im, text, x, y, w, h, fg, slant=False):
    mask = lettering(text, w, h, slant)
    im.paste(fg, (x, y, x+w, y+h), mask)


def emblem(svg, size):
    path = ElementTree.parse(svg).getroot().find('{http://www.w3.org/2000/svg}path').attrib['d']
    outline = Outline()
    parse_path(path, outline)
    large = (size[0]*2, size[1]*2)
    mask = Image.new('L',large)
    for ring in outline.rings:
        part = Image.new('L', large)
        ImageDraw.Draw(part).polygon([(x/24*(large[0]-8)+4, y/24*(large[1]-8)+4) for x,y in ring], fill=255)
        mask = ImageChops.difference(mask, part)
    im = gradient(large, [(0,'95703a'),(.16,'fff2bb'),(.32,'be883f'),(.51,'fce3a3'),(.57,'a16832'),(.91,'dbac57'),(1,'664021')])
    im.putalpha(mask)
    return im.resize(size, RESAMPLE)


def plate(size, province, prefix, number, rear):
    w,h = size
    out = Image.new('RGBA',size)
    d = ImageDraw.Draw(out)
    d.rounded_rectangle((1,1,w-2,h-2), radius=10, fill='#3c3c3c', outline='#5e605e', width=3)
    edge = 8 if rear else 5
    inner = (w-edge*2, h-edge*2)
    bg = gradient(inner, [(0,'ffd25a' if rear else 'd9d8d0'),(.36,'f9c133' if rear else 'f4f1e7'),(1,'d89e27' if rear else 'cbc9c1')])
    out.alpha_composite(bg,(edge,edge))
    d.rounded_rectangle((edge,edge,w-edge-1,h-edge-1), radii_fix(inner), outline='#c59640' if rear else '#b1b2aa', width=2)
    # Compact left column: province and two small security marks.
    crest_x, crest_y = (40,38) if rear else (32,27)
    trim = '#846936' if rear else '#858882'
    d.ellipse((crest_x-11,crest_y-10,crest_x+11,crest_y+12), outline=trim, width=2)
    for i in range(8):
        d.ellipse((crest_x-4+i%4*2,crest_y-4+i//4*4,crest_x-3+i%4*2,crest_y+i//4*4), fill=trim)
    d.line((crest_x-9,crest_y+13,crest_x+9,crest_y+13), fill=trim, width=2)
    d.ellipse((crest_x+22,crest_y-7,crest_x+37,crest_y+8), fill='#d5af62' if rear else '#a7a9a5')
    fg = '#262821'
    stamp(out,province, 20 if rear else 17,60 if rear else 43, 61 if rear else 49, 47 if rear else 38,fg)
    if rear:
        stamp(out,prefix, 93, 23, 169, 72,fg)
        stamp(out,number, 49,103,204,74,fg)
    else:
        stamp(out,prefix+' '+number,91,19,w-108,62,fg)
    return out


def radii_fix(size): return max(3, min(size[0],size[1])//22)


def metallic_word(text, size, w, h, stops, outside, slant=True):
    from PIL import ImageFilter
    mask = Image.new('L',size)
    mask.paste(lettering(text, w, h, slant),((size[0]-w)//2,(size[1]-h)//2))
    edge = mask.filter(ImageFilter.MaxFilter(5))
    out = Image.new('RGBA',size, outside)
    out.putalpha(edge)
    shine = gradient(size,stops)
    shine.putalpha(mask)
    out.alpha_composite(shine)
    return out


def hybrid(size):
    w,h = size
    out = Image.new('RGBA',size)
    d = ImageDraw.Draw(out)
    d.polygon([(10,4),(w-3,4),(w-12,h-5),(2,h-5)],fill='#d6dce0',outline='#686c72',width=2)
    d.polygon([(18,8),(52,8),(46,h-10),(12,h-10)],fill='#4cbee5')
    stamp(out,'HYBRID',57,10,w-78,h-22,'#304d68',True)
    return out


def plaque(size):
    w,h = size
    out = gradient(size,[(0,'d7ab48'),(.4,'89653b'),(.75,'c9a268'),(1,'e5be77')])
    d = ImageDraw.Draw(out)
    d.rectangle((2,2,w-3,h-3),outline='#eccf8b',width=3)
    d.rectangle((7,7,w-8,h-8),outline='#6e4929',width=1)
    d.polygon([(14,22),(w-14,22),(w-14,49),(w//2,65),(14,49)],outline='#e1c27e',width=2)
    d.line((w//2,25,w//2,59),fill='#ebce8b',width=1)
    d.line((16,43,w-17,43),fill='#ebce8b',width=1)
    for x in (20,35):
        d.ellipse((x-4,29,x+4,36),outline='#ddbe7d')
    d.arc((11,47,w-12,74),11,167,fill='#edca79',width=2)
    stamp(out, 'HANWELLA', 8, 74, w-16, 5, '#725235')
    return out


def make_atlas(output, honda_svg, plate_number=None, province='LK'):
    prefix, suffix = plate_number.split() if plate_number else ('VEZEL','RU3')
    atlas = Image.new('RGBA',(512,512))
    def insert(name, generator):
        x,y,w,h = ATLAS[name]
        atlas.alpha_composite(generator((w,h)),(x,y))
    insert('front',lambda s: plate(s,province,prefix,suffix,False))
    insert('rear',lambda s: plate(s,province,prefix,suffix,True))
    insert('honda',lambda s: emblem(honda_svg,s))
    insert('vezel',lambda s: metallic_word('VEZEL',s,189,30,[(0,'b88744'),(.34,'f7d98e'),(.52,'ffefb4'),(.68,'ba8746'),(1,'965f35')],'#654229'))
    insert('hybrid',hybrid)
    insert('rs',lambda s: metallic_word('RS',s,120,33,[(0,'f5b0a5'),(.25,'e55d53'),(.85,'a72b34'),(1,'68262b')],'#d9d8d4'))
    insert('plaque',plaque)
    atlas.save(output,optimize=True)
