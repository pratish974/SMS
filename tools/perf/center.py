import re, sys
# Prints "x y" for the first node whose text or content-desc equals argv[1]. Nothing else is printed.
x = sys.stdin.read()
label = sys.argv[1]
for m in re.finditer(r'<node [^>]*>', x):
    n = m.group(0)
    if f'text="{label}"' in n or f'content-desc="{label}"' in n:
        b = list(map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()))
        print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
        break
