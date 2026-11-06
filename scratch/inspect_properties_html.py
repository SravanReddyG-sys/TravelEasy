with open(r'frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "activeNav === 'properties'" in line:
        print(f"Found properties section at line {i+1}")
        for j in range(i, min(i + 150, len(lines))):
            print(f"{j+1}: {lines[j]}", end='')
        break
