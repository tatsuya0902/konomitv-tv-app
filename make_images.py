from PIL import Image, ImageDraw, ImageFont

BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
BG = (28, 17, 14)
PINK = (236, 64, 122)
WHITE = (255, 255, 255)
OUT = "/home/hatch/workspace/konomitv-tv-app/res/drawable"

# banner 320x180 (Android TV launcher)
img = Image.new("RGB", (320, 180), BG)
d = ImageDraw.Draw(img)
d.rounded_rectangle([24, 55, 94, 125], radius=14, fill=PINK)
d.polygon([(50, 76), (50, 104), (76, 90)], fill=WHITE)
f = ImageFont.truetype(BOLD, 40)
d.text((112, 68), "KonomiTV", font=f, fill=WHITE)
img.save(f"{OUT}/banner.png")

# icon 192x192
img = Image.new("RGB", (192, 192), BG)
d = ImageDraw.Draw(img)
d.rounded_rectangle([26, 46, 166, 146], radius=18, fill=PINK)
d.polygon([(76, 72), (76, 120), (122, 96)], fill=WHITE)
img.save(f"{OUT}/icon.png")
print("IMAGES_OK")
