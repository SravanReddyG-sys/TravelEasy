with open(r'hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i in range(230, min(360, len(lines))):
    print(f"{i+1}: {lines[i]}", end='')
