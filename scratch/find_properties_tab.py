with open(r'frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "activeNav === 'properties'" in line and "<div" in line:
        print(f"Found activeNav === 'properties' container at line {i+1}")
        for j in range(i, min(i + 80, len(lines))):
            safe_line = lines[j].encode('ascii', errors='replace').decode('ascii')
            print(f"{j+1}: {safe_line}", end='')
        break
