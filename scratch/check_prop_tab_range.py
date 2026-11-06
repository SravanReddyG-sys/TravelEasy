with open(r'frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "activeNav === 'properties'" in line and "<div" in line:
        start_idx = i
        # find matching closing div or next tab
        for j in range(start_idx, min(start_idx + 100, len(lines))):
            if "activeNav === 'rooms'" in lines[j]:
                end_idx = j - 1
                print(f"Properties tab spans lines {start_idx+1} to {end_idx+1}")
                break
        break
