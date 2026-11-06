with open(r'hotel-service/src/test/java/com/traveleasy/hotel/HotelStep1RegistrationTest.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "testPropertySegment_Validation" in line:
        for j in range(max(0, i-4), min(i+18, len(lines))):
            print(f"{j+1}: {repr(lines[j])}")
        break
