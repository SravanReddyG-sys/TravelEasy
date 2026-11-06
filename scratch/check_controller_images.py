with open(r'hotel-service/src/main/java/com/traveleasy/hotel/controller/HotelController.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "/images/upload" in line:
        for j in range(max(0, i-5), min(i+70, len(lines))):
            print(f"{j+1}: {lines[j]}", end='')
        break
