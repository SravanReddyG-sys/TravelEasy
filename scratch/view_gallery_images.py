with open(r'hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "// Persist gallery images" in line:
        for j in range(i, min(i+40, len(lines))):
            print(lines[j], end='')
        break
