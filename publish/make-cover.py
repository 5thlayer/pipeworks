# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

# Temporary cover and icon, until an artist draws the real ones, in the style
# of Wireworks' and Groundworks' publish/make-cover.py. Run from the repo root with Pillow installed.
# The pipe and tank are cut from this Library's own textures (Oritech's, CC0), so no
# Minecraft sources are needed. Impact and Arial Black are used where they exist, as on
# the other Libraries' covers; elsewhere the nearest bold sans.
import os
from PIL import Image, ImageDraw, ImageFont
P='src/main/resources/assets/pipeworks/textures/block/'
BG=(24,26,32)
ACCENT=(70,190,210)   # the fluid, a segment's one colour
FLOOR=(38,41,50)
PIPE_SIDE=(0,0,16,11)      # pipe.png: a straight pipe's side, two copper bands
TANK_GLASS=(32,0,46,14)    # storage_tank.png: the glass side
TANK_RIM=(48,0,64,6)       # storage_tank.png: the copper rim
LEVEL=0.6  # one segment, so every tank shows the same fill
FADE=60  # 0 leaves the scene at full strength, 255 hides it
_cache={}
def crop(name,box,w,h):
    if (name,box,w,h) not in _cache:
        _cache[name,box,w,h]=Image.open(P+name+'.png').convert('RGBA').crop(box).resize((w,h),Image.NEAREST)
    return _cache[name,box,w,h]
def font(names,size):
    for n in names:
        if os.path.exists(n):
            return ImageFont.truetype(n,size)
    raise SystemExit('no font found among '+', '.join(names))
def tank(img,x,y,s,level):
    # a storage tank: glass between copper rims, filled to its segment's level
    rim=s//6
    img.alpha_composite(crop('storage_tank',TANK_GLASS,s,2*s-2*rim),(x,y+rim))
    h=int((2*s-2*rim)*level)
    fill=Image.new('RGBA',(s-2*rim,h),ACCENT+(200,))
    img.alpha_composite(fill,(x+rim,y+2*s-rim-h))
    img.alpha_composite(crop('storage_tank',TANK_RIM,s,rim),(x,y))
    img.alpha_composite(crop('storage_tank',TANK_RIM,s,rim),(x,y+2*s-rim))
def pipe(img,x0,x1,y,s):
    # a straight run of pipes, block by block
    h=int(s*0.5); x=x0
    while x<x1:
        w=min(s,x1-x)
        img.alpha_composite(crop('pipe',PIPE_SIDE,s,h).crop((0,0,w,h)),(x,y-h//2))
        x+=s
def make(W,H,out,title_size,s):
    img=Image.new('RGBA',(W,H),BG+(255,))
    ground=H-s//2
    ImageDraw.Draw(img).rectangle([0,ground,W,H],fill=FLOOR)
    # tanks along the floor, a pipe run joining them at half their height
    pitch=s*4; n=(W-s)//pitch+1; x0=(W-(n-1)*pitch-s)//2
    xs=[x0+i*pitch for i in range(n)]
    pipe(img,0,W,ground-s,s)
    for x in xs:
        tank(img,x,ground-2*s,s,LEVEL)
    img=Image.alpha_composite(img,Image.new('RGBA',(W,H),BG+(FADE,)))
    bh=int(title_size*1.6); cy=int(H*0.3)
    band=Image.new('RGBA',(W,H),(0,0,0,0)); bd=ImageDraw.Draw(band)
    bd.rectangle([0,cy-bh//2,W,cy+bh//2],fill=(16,17,22,225))
    img=Image.alpha_composite(img,band)
    d=ImageDraw.Draw(img)
    lt=max(3,title_size//25)
    d.rectangle([0,cy-bh//2,W,cy-bh//2+lt],fill=ACCENT); d.rectangle([0,cy+bh//2-lt,W,cy+bh//2],fill=ACCENT)
    f1=font(['/System/Library/Fonts/Supplemental/Impact.ttf','/usr/share/fonts/gsfonts/NimbusSansNarrow-Bold.otf',
             '/usr/share/fonts/liberation/LiberationSansNarrow-Bold.ttf'],title_size)
    f2=font(['/System/Library/Fonts/Supplemental/Arial Black.ttf','/usr/share/fonts/liberation/LiberationSans-Bold.ttf',
             '/usr/share/fonts/gsfonts/NimbusSans-Bold.otf'],int(title_size*0.62))
    a,w='PIPE','works'
    b1=d.textbbox((0,0),a,font=f1); b2=d.textbbox((0,0),w,font=f2)
    w1=b1[2]-b1[0]; w2=b2[2]-b2[0]; h2=b2[3]-b2[1]
    px=int(title_size*0.16); gap=int(title_size*0.1)
    bw=w2+2*px; tot=w1+gap+bw; x=(W-tot)//2
    h1=b1[3]-b1[1]; y=cy-h1//2-b1[1]; sh=max(3,title_size//20)
    d.text((x+sh-b1[0],y+sh),a,font=f1,fill=(0,0,0))
    d.text((x-b1[0],y),a,font=f1,fill=ACCENT)
    bx=x+w1+gap; bt=cy-h1//2; bb=bt+h1
    d.rounded_rectangle([bx+sh,bt+sh,bx+bw+sh,bb+sh],radius=px,fill=(0,0,0))
    d.rounded_rectangle([bx,bt,bx+bw,bb],radius=px,fill=(240,240,236))
    ty=bt+(h1-h2)//2-b2[1]
    d.text((bx+px-b2[0],ty),w,font=f2,fill=BG)
    img.convert('RGB').save(out)
make(1280,640,'publish/pipeworks-cover.png',150,96)
make(512,512,'publish/pipeworks-icon.png',80,64)
