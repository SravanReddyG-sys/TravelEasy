with open(r'frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "step1Data.propertyClassification" in line:
        for j in range(max(0, i-5), min(i+50, len(lines))):
            safe = lines[j].encode('ascii', errors='replace').decode('ascii')
            print(f"{j+1}: {safe}", end='')
        break
