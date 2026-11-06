with open(r'frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.ts', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "loadInitialData():" in line or "loadInitialData()" in line:
        if "void" in line or "{" in line or "loadInitialData() {" in line or "loadInitialData(): void" in line:
            print(f"Def at line {i+1}: {line}")
            for j in range(i, min(i+25, len(lines))):
                safe = lines[j].encode('ascii', errors='replace').decode('ascii')
                print(f"  {j+1}: {safe}", end='')
            break
