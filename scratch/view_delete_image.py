with open(r'hotel-service/src/main/java/com/traveleasy/hotel/controller/HotelController.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "@DeleteMapping(\"/images/{filename:.+}\")" in line:
        for j in range(max(0, i-5), min(i+45, len(lines))):
            print(lines[j], end='')
        break
