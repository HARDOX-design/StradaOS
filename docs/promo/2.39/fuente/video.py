# Video de ejemplo original (carretera nocturna estilo synthwave) para las capturas promocionales
import math, subprocess, sys
from PIL import Image, ImageDraw, ImageFilter
W,H,FPS,SEC=640,360,24,14
out=sys.argv[1]
p=subprocess.Popen(['ffmpeg','-v','error','-y','-f','rawvideo','-pix_fmt','rgb24','-s',f'{W}x{H}','-r',str(FPS),'-i','-',
  '-c:v','libx264','-pix_fmt','yuv420p','-profile:v','baseline','-level','3.0','-movflags','+faststart',out],stdin=subprocess.PIPE)
hz=int(H*0.55)
# fondo fijo: cielo degradado + sol con franjas + montañas
bg=Image.new('RGB',(W,H)); d=ImageDraw.Draw(bg)
for y in range(hz):
    t=y/hz; c=(int(20+t*160),int(8+t*30),int(50+t*60)); d.line([(0,y),(W,y)],fill=c)
for y in range(hz,H):
    d.line([(0,y),(W,y)],fill=(14,6,26))
sun=Image.new('L',(W,H),0); sd=ImageDraw.Draw(sun); r=78; cx,cy=W//2,hz-18
sd.ellipse([cx-r,cy-r,cx+r,cy+r],fill=255)
for i in range(6):
    yy=cy+8+i*11; sd.rectangle([0,yy,W,yy+2+i],fill=0)
sd.rectangle([0,hz,W,H],fill=0)
sunc=Image.new('RGB',(W,H))
for y in range(H):
    t=max(0,min(1,(y-(cy-r))/(2*r))); ImageDraw.Draw(sunc).line([(0,y),(W,y)],fill=(255,int(210-150*t),int(60+40*t)))
glow=sun.filter(ImageFilter.GaussianBlur(18))
bg=Image.composite(Image.new('RGB',(W,H),(255,90,80)),bg,glow.point(lambda v:int(v*0.5)))
bg=Image.composite(sunc,bg,sun)
d=ImageDraw.Draw(bg)
import random; random.seed(4)
for k,(col,amp,base) in enumerate([((60,20,80),40,hz-10),((35,12,55),26,hz)]):
    pts=[(0,hz)]
    for x in range(0,W+20,20): pts.append((x,base-amp*abs(math.sin(x*0.013+k*2))-random.randint(0,12)))
    pts.append((W,hz)); d.polygon(pts,fill=col)
for i in range(60):
    x,y=random.randint(0,W),random.randint(0,hz-90); d.point((x,y),fill=(255,230,255))
for f in range(FPS*SEC):
    im=bg.copy(); g=ImageDraw.Draw(im)
    ph=(f/FPS*1.2)%1
    for i in range(14):
        z=(i+ph)/14; y=hz+(H-hz)*z**2.2
        g.line([(0,y),(W,y)],fill=(255,60,140),width=1+int(z*2))
    for i in range(-12,13):
        g.line([(W/2+i*8,hz),(W/2+i*95,H)],fill=(255,60,140),width=1)
    # carretera
    g.polygon([(W/2-6,hz),(W/2+6,hz),(W/2+170,H),(W/2-170,H)],fill=(22,10,34))
    for i in range(8):
        z=((i+ph*2)%8)/8; z2=z+0.05
        y1=hz+(H-hz)*z**2.2; y2=hz+(H-hz)*min(1,z2)**2.2
        w1=1+z*6; w2=1+z2*6
        g.polygon([(W/2-w1/2,y1),(W/2+w1/2,y1),(W/2+w2/2,y2),(W/2-w2/2,y2)],fill=(255,220,120))
    g.line([(W/2-6,hz),(W/2-170,H)],fill=(255,90,180),width=2); g.line([(W/2+6,hz),(W/2+170,H)],fill=(255,90,180),width=2)
    p.stdin.write(im.tobytes())
p.stdin.close(); p.wait()
