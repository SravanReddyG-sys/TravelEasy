with open(r'hotel-service/src/main/java/com/traveleasy/hotel/controller/HotelController.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "getMyDrafts" in line:
        for j in range(max(0, i-2), min(i+15, len(lines))):
            print(f"{j+1}: {repr(lines[j])}")
        break
