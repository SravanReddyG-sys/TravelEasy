with open(r'hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "SUPPORTED_SEGMENTS" in line or "SUPPORTED_CLASSIFICATIONS" in line:
        print(f"Line {i+1}:")
        for j in range(max(0, i-5), min(i+15, len(lines))):
            print(lines[j], end='')
        break
