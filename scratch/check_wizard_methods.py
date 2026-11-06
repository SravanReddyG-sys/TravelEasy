with open(r'frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.ts', 'r', encoding='utf-8') as f:
    lines = f.readlines()

print(f"Total lines: {len(lines)}")
for i in range(max(0, len(lines) - 250), len(lines)):
    safe = lines[i].encode('ascii', errors='replace').decode('ascii')
    print(f"{i+1}: {safe}", end='')
