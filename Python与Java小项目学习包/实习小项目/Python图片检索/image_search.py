from pathlib import Path
import argparse
import math
import csv
from PIL import Image, ImageOps

EXTS = {'.jpg', '.jpeg', '.png', '.bmp', '.webp'}

def feature(path):
    # 颜色直方图：每通道量化为16档，再归一化。
    with Image.open(path) as raw:
        im = ImageOps.exif_transpose(raw).convert('RGB').resize((128, 128))
        hist = im.histogram()
    values = [sum(hist[c*256+b*16:c*256+(b+1)*16])
              for c in range(3) for b in range(16)]
    norm = math.sqrt(sum(v*v for v in values))
    return [v/norm for v in values]

def search(query, folder, k):
    query = Path(query).resolve()
    target = feature(query)
    results = []
    for path in Path(folder).rglob('*'):
        if not path.is_file() or path.suffix.lower() not in EXTS or path.resolve() == query:
            continue
        try:
            vector = feature(path)
            score = sum(a*b for a, b in zip(target, vector))
            results.append((str(path), score))
        except (OSError, ValueError) as exc:
            print(f'跳过无法读取的图片：{path} ({exc})')
    return sorted(results, key=lambda x: (-x[1], x[0]))[:k]

def main():
    p = argparse.ArgumentParser(description='基于颜色直方图和余弦相似度的本地图片检索')
    p.add_argument('--query', required=True)
    p.add_argument('--folder', required=True)
    p.add_argument('--top', type=int, default=5)
    p.add_argument('--output', default='results.csv')
    args = p.parse_args()
    if args.top < 1 or not Path(args.folder).is_dir():
        p.error('top必须为正整数，folder必须是存在的目录')
    try:
        rows = search(args.query, args.folder, args.top)
    except (OSError, ValueError) as exc:
        p.error(str(exc))
    with open(args.output, 'w', newline='', encoding='utf-8-sig') as f:
        w = csv.writer(f)
        w.writerow(['图片路径', '颜色相似度（非识别置信度）'])
        w.writerows(rows)
    for path, score in rows:
        print(f'{score:.4f}  {path}')
    print(f'找到{len(rows)}项，结果已写入 {args.output}')

if __name__ == '__main__':
    main()
