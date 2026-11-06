with open(r'hotel-service/src/main/java/com/traveleasy/hotel/service/HotelPropertyService.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "private void validateStep1Request" in line:
        print(f"Line {i+1}:")
        for j in range(i, min(i+60, len(lines))):
            print(lines[j], end='')
        break
