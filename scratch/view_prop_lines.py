with open(r'frontend/src/app/components/hotel-manager-dashboard/hotel-manager-dashboard.component.html', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i in range(739, 784):
    safe = lines[i].encode('ascii', errors='replace').decode('ascii')
    print(f"{i+1}: {safe}", end='')
