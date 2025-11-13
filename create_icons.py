from PIL import Image, ImageDraw
import os

# Define icon sizes for different densities
sizes = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
}

# Create res directory path
res_path = r'D:\Development\Projects\Mobile\Android\CryptoTrader\app\src\main\res'

for density, size in sizes.items():
    # Create mipmap directory
    mipmap_dir = os.path.join(res_path, f'mipmap-{density}')
    os.makedirs(mipmap_dir, exist_ok=True)

    # Create ic_launcher.png
    img = Image.new('RGBA', (size, size), (76, 175, 80, 255))  # Green color
    draw = ImageDraw.Draw(img)

    # Draw a simple "C" shape
    draw.ellipse([size//4, size//4, 3*size//4, 3*size//4], fill=(255, 255, 255, 255))
    draw.ellipse([3*size//8, 3*size//8, 5*size//8, 5*size//8], fill=(76, 175, 80, 255))
    draw.rectangle([size//2, size//4, 3*size//4, 3*size//4], fill=(76, 175, 80, 255))

    img.save(os.path.join(mipmap_dir, 'ic_launcher.png'))

    # Create ic_launcher_round.png (same but round)
    img_round = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw_round = ImageDraw.Draw(img_round)
    draw_round.ellipse([0, 0, size, size], fill=(76, 175, 80, 255))
    draw_round.ellipse([size//4, size//4, 3*size//4, 3*size//4], fill=(255, 255, 255, 255))
    draw_round.ellipse([3*size//8, 3*size//8, 5*size//8, 5*size//8], fill=(76, 175, 80, 255))
    draw_round.rectangle([size//2, size//4, size, 3*size//4], fill=(76, 175, 80, 255))

    img_round.save(os.path.join(mipmap_dir, 'ic_launcher_round.png'))

    print(f'Created icons for {density}')

print('All icons created successfully!')
