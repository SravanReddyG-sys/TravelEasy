with open(r'frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.ts', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "loadInitialData" in line:
        print(f"Line {i+1}: {line}", end='')
        for j in range(i, min(i+40, len(lines))):
            safe = lines[j].encode('ascii', errors='replace').decode('ascii')
            print(f"  {j+1}: {safe}", end='')
        break
