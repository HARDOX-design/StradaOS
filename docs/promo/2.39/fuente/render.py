import sys, asyncio
from playwright.async_api import async_playwright
from PIL import Image
jobs=[]
for n in range(1,8):
    for L in ('es','en'): jobs.append(('land',L,n))
    jobs.append(('port','es',n))
only=sys.argv[1:]  # filtros opcionales «land-es-3»
async def main():
    async with async_playwright() as p:
        b=await p.chromium.launch()
        for fmt,L,n in jobs:
            name=f'{fmt}-{L}-{n}'
            if only and name not in only: continue
            w,h,sc=(1600,900,1.25) if fmt=='land' else (1080,1350,4/3)
            pg=await b.new_page(viewport={'width':w,'height':h},device_scale_factor=sc)
            await pg.goto(f'file:///home/claude/promo/promo.html?fmt={fmt}&lang={L}&n={n}')
            await pg.wait_for_load_state('networkidle'); await pg.wait_for_timeout(400)
            png=f'/home/claude/promo/out/{name}.png'
            await pg.screenshot(path=png,clip={'x':0,'y':0,'width':w,'height':h})
            Image.open(png).convert('RGB').save(png.replace('.png','.jpg'),quality=92,optimize=True)
            await pg.close(); print(name)
        await b.close()
asyncio.run(main())
