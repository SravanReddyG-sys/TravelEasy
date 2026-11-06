with open('HOTEL_MANAGER_PORTAL_IMPLEMENTATION_TRACKER.md', 'r', encoding='utf-8') as f:
    text = f.read()

idx = text.find("## 6. WP-05: Hotel Manager Portal")
if idx != -1:
    print(text[idx:idx+2500])
