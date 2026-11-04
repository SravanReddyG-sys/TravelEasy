with open('frontend/src/app/components/admin-dashboard/admin-dashboard.component.html', 'r', encoding='utf-8') as f:
    content = f.read()

target = '<main class="dashboard-content">'
pos = content.find(target)
print("pos:", pos)
print("Before target:\n", content[pos-200:pos])
