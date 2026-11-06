with open(r'hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "saveStep1Draft" in line:
        for j in range(i, min(i+70, len(lines))):
            print(lines[j], end='')
        break
