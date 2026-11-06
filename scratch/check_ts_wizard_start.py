with open(r'frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.ts', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "startPropertyRegistration" in line:
        for j in range(max(0, i-5), min(i+50, len(lines))):
            print(f"{j+1}: {lines[j]}", end='')
        break
